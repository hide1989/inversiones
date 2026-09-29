package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTransactionsUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private ListTransactionsUseCase useCase;

    private static Transaction trade(String ticker, String broker, int day) {
        return Transaction.builder()
                .ticker(Ticker.of(ticker)).brokerName(broker).type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(LocalDate.of(2026, 6, day)).build();
    }

    @Test
    void returnsEverythingNewestFirstWhenNoFilterIsGiven() {
        when(transactionRepository.findAll())
                .thenReturn(List.of(trade("QQQ", "Hapi", 1), trade("VOO", "ARQ", 5)));

        List<Transaction> result = useCase.execute(TransactionFilter.none());

        assertThat(result).extracting(t -> t.ticker().symbol()).containsExactly("VOO", "QQQ");
    }

    @Test
    void filtersByTickerBrokerAndDateRange() {
        when(transactionRepository.findAll()).thenReturn(List.of(
                trade("QQQ", "Hapi", 1),
                trade("QQQ", "ARQ", 5),
                trade("QQQ", "Hapi", 20),
                trade("VOO", "Hapi", 10)));

        List<Transaction> result = useCase.execute(new TransactionFilter(
                Ticker.of("QQQ"), "Hapi", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 30)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tradedAt()).isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void anEmptyRepositoryProducesAnEmptyList() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        assertThat(useCase.execute(TransactionFilter.none())).isEmpty();
    }
}
