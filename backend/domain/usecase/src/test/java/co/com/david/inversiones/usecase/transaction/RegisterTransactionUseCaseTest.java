package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.exception.InsufficientPositionException;
import co.com.david.inversiones.model.exception.InvalidTradeDateException;
import co.com.david.inversiones.model.exception.UnknownInstrumentException;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
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
class RegisterTransactionUseCaseTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final String HAPI = "Hapi";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private TimeProvider timeProvider;
    @InjectMocks
    private RegisterTransactionUseCase useCase;

    private Instrument qqq;

    @BeforeEach
    void setUp() {
        qqq = Instrument.builder()
                .ticker(QQQ).name("Invesco QQQ Trust").type(InstrumentType.ETF)
                .sector("Broad Market").exchange("NASDAQ").currency(Currency.USD)
                .build();
    }

    private static Transaction trade(TransactionType type, String quantity, String price, LocalDate date) {
        return Transaction.builder()
                .ticker(QQQ).brokerName(HAPI).type(type)
                .quantity(Quantity.of(quantity)).unitPrice(Money.usd(price))
                .tradedAt(date).build();
    }

    @Test
    void savesAPurchaseOfAKnownInstrument() {
        Transaction candidate = trade(TransactionType.BUY, "1", "600.00", TODAY);
        when(timeProvider.today()).thenReturn(TODAY);
        when(instrumentRepository.findByTicker(QQQ)).thenReturn(Optional.of(qqq));
        when(transactionRepository.save(candidate)).thenReturn(candidate);

        assertThat(useCase.execute(candidate)).isSameAs(candidate);
        verify(transactionRepository).save(candidate);
    }

    @Test
    void rejectsATransactionForAnUnknownInstrument() {
        Transaction candidate = trade(TransactionType.BUY, "1", "600.00", TODAY);
        when(instrumentRepository.findByTicker(QQQ)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(candidate))
                .isInstanceOf(UnknownInstrumentException.class)
                .hasMessageContaining("QQQ");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsATradeDatedInTheFuture() {
        Transaction candidate = trade(TransactionType.BUY, "1", "600.00", TODAY.plusDays(1));
        when(timeProvider.today()).thenReturn(TODAY);
        when(instrumentRepository.findByTicker(QQQ)).thenReturn(Optional.of(qqq));

        assertThatThrownBy(() -> useCase.execute(candidate))
                .isInstanceOf(InvalidTradeDateException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsASaleLargerThanTheSharesHeld() {
        Transaction existingBuy = trade(TransactionType.BUY, "1", "600.00", TODAY.minusDays(10));
        Transaction candidate = trade(TransactionType.SELL, "2", "620.00", TODAY);
        when(timeProvider.today()).thenReturn(TODAY);
        when(instrumentRepository.findByTicker(QQQ)).thenReturn(Optional.of(qqq));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(existingBuy));

        assertThatThrownBy(() -> useCase.execute(candidate))
                .isInstanceOf(InsufficientPositionException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void acceptsASaleThatFitsWithinTheSharesHeld() {
        Transaction existingBuy = trade(TransactionType.BUY, "3", "600.00", TODAY.minusDays(10));
        Transaction candidate = trade(TransactionType.SELL, "2", "620.00", TODAY);
        when(timeProvider.today()).thenReturn(TODAY);
        when(instrumentRepository.findByTicker(QQQ)).thenReturn(Optional.of(qqq));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(existingBuy));
        when(transactionRepository.save(candidate)).thenReturn(candidate);

        assertThat(useCase.execute(candidate)).isSameAs(candidate);
    }
}
