package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.exception.InsufficientPositionException;
import co.com.david.inversiones.model.exception.InvalidTransactionUpdateException;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateTransactionUseCaseTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final Ticker VOO = Ticker.of("VOO");
    private static final String HAPI = "Hapi";
    private static final String ARQ = "ARQ";

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TimeProvider timeProvider;
    @InjectMocks
    private UpdateTransactionUseCase useCase;

    private static Transaction trade(Long id, Ticker ticker, String broker, TransactionType type,
                                      String quantity, int day) {
        return Transaction.builder()
                .id(id).ticker(ticker).brokerName(broker).type(type)
                .quantity(Quantity.of(quantity)).unitPrice(Money.usd("600.00"))
                .tradedAt(LocalDate.of(2026, 6, day)).build();
    }

    @Test
    void savesTheReplacementWhenTheResultingHistoryStaysConsistent() {
        Transaction existing = trade(1L, QQQ, HAPI, TransactionType.BUY, "3", 1);
        Transaction replacement = trade(1L, QQQ, HAPI, TransactionType.BUY, "2", 1);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(existing));
        when(transactionRepository.save(replacement)).thenReturn(replacement);
        when(timeProvider.today()).thenReturn(TODAY);

        Transaction result = useCase.execute(1L, replacement);

        assertThat(result).isSameAs(replacement);
        verify(transactionRepository).save(replacement);
    }

    @Test
    void rejectsAnEditThatMovesTheTradeToAnotherInstrument() {
        Transaction existing = trade(1L, QQQ, HAPI, TransactionType.BUY, "2", 1);
        Transaction replacement = trade(1L, VOO, HAPI, TransactionType.BUY, "2", 1);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(1L, replacement))
                .isInstanceOf(InvalidTransactionUpdateException.class)
                .hasMessageContaining("QQQ")
                .hasMessageContaining("VOO");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsAnEditThatMovesTheTradeToAnotherBroker() {
        Transaction existing = trade(1L, QQQ, HAPI, TransactionType.BUY, "2", 1);
        Transaction replacement = trade(1L, QQQ, ARQ, TransactionType.BUY, "2", 1);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(1L, replacement))
                .isInstanceOf(InvalidTransactionUpdateException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsAnEditThatWouldMakeTheResultingHistoryImplyANegativeHolding() {
        Transaction purchase = trade(1L, QQQ, HAPI, TransactionType.BUY, "3", 1);
        Transaction sale = trade(2L, QQQ, HAPI, TransactionType.SELL, "3", 2);
        Transaction replacement = trade(1L, QQQ, HAPI, TransactionType.BUY, "1", 1);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(purchase, sale));
        when(timeProvider.today()).thenReturn(TODAY);

        assertThatThrownBy(() -> useCase.execute(1L, replacement))
                .isInstanceOf(InsufficientPositionException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsAnEditThatMovesTheTradeDateIntoTheFuture() {
        Transaction existing = trade(1L, QQQ, HAPI, TransactionType.BUY, "2", 1);
        Transaction replacement = trade(1L, QQQ, HAPI, TransactionType.BUY, "2", 20);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(timeProvider.today()).thenReturn(TODAY);

        assertThatThrownBy(() -> useCase.execute(1L, replacement))
                .isInstanceOf(co.com.david.inversiones.model.exception.InvalidTradeDateException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownTransactionId() {
        Transaction replacement = trade(99L, QQQ, HAPI, TransactionType.BUY, "1", 1);
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(99L, replacement))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
