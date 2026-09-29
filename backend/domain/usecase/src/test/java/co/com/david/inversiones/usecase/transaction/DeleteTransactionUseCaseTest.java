package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.exception.InsufficientPositionException;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteTransactionUseCaseTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final String HAPI = "Hapi";

    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private DeleteTransactionUseCase useCase;

    private static Transaction trade(Long id, TransactionType type, String quantity, int day) {
        return Transaction.builder()
                .id(id).ticker(QQQ).brokerName(HAPI).type(type)
                .quantity(Quantity.of(quantity)).unitPrice(Money.usd("600.00"))
                .tradedAt(LocalDate.of(2026, 6, day)).build();
    }

    @Test
    void deletesATransactionWhenTheRemainingHistoryStaysConsistent() {
        Transaction first = trade(1L, TransactionType.BUY, "2", 1);
        Transaction second = trade(2L, TransactionType.BUY, "3", 2);
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(second));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(first, second));

        useCase.execute(2L);

        verify(transactionRepository).deleteById(2L);
    }

    @Test
    void refusesToDeleteAPurchaseThatALaterSaleDependsOn() {
        Transaction purchase = trade(1L, TransactionType.BUY, "2", 1);
        Transaction sale = trade(2L, TransactionType.SELL, "2", 2);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactionRepository.findByTickerAndBroker(QQQ, HAPI)).thenReturn(List.of(purchase, sale));

        assertThatThrownBy(() -> useCase.execute(1L))
                .isInstanceOf(InsufficientPositionException.class);

        verify(transactionRepository, never()).deleteById(anyLong());
    }

    @Test
    void rejectsAnUnknownTransactionId() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(99L))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
