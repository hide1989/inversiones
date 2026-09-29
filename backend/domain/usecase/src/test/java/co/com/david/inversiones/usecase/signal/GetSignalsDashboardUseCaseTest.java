package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSignalsDashboardUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);
    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final Ticker TSLA = Ticker.of("TSLA");
    private static final Ticker EC = Ticker.of("EC");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SignalSnapshotRepository snapshotRepository;
    @InjectMocks
    private GetSignalsDashboardUseCase useCase;

    private static Transaction holding(Ticker ticker) {
        return Transaction.builder()
                .ticker(ticker).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(DATE.minusMonths(3)).build();
    }

    private static SignalSnapshot snapshot(Ticker ticker, Map<SignalSource, BigDecimal> scores) {
        return SignalSnapshot.builder().ticker(ticker).snapshotDate(DATE).scores(scores).build();
    }

    @Test
    void ordersTickersByCombinedScoreDescending() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ), holding(TSLA)));
        when(snapshotRepository.findLatest(QQQ)).thenReturn(Optional.of(
                snapshot(QQQ, Map.of(SignalSource.TECHNICAL, new BigDecimal("9")))));
        when(snapshotRepository.findLatest(TSLA)).thenReturn(Optional.of(
                snapshot(TSLA, Map.of(SignalSource.TECHNICAL, new BigDecimal("4")))));

        SignalsDashboard dashboard = useCase.execute();

        assertThat(dashboard.signals()).extracting(signal -> signal.ticker().symbol())
                .containsExactly("QQQ", "TSLA");
        assertThat(dashboard.averageScore()).isEqualByComparingTo("6.50");
        assertThat(dashboard.levelCounts()).containsEntry(SignalLevel.STRONG_BUY, 1);
        assertThat(dashboard.levelCounts()).containsEntry(SignalLevel.SELL, 1);
    }

    @Test
    void excludesATickerWithNoSourcesFromTheAverageAndTheLevelCounts() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ), holding(EC)));
        when(snapshotRepository.findLatest(QQQ)).thenReturn(Optional.of(
                snapshot(QQQ, Map.of(SignalSource.TECHNICAL, new BigDecimal("8")))));
        when(snapshotRepository.findLatest(EC)).thenReturn(Optional.of(snapshot(EC, Map.of())));

        SignalsDashboard dashboard = useCase.execute();

        assertThat(dashboard.averageScore()).isEqualByComparingTo("8.00");
        assertThat(dashboard.levelCounts().values()).containsExactly(1);
        assertThat(dashboard.tickersWithoutData()).containsExactly(EC);
        assertThat(dashboard.signals()).hasSize(2);
    }

    @Test
    void treatsATickerWithNoStoredSnapshotAsWithoutData() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ)));
        when(snapshotRepository.findLatest(QQQ)).thenReturn(Optional.empty());

        SignalsDashboard dashboard = useCase.execute();

        assertThat(dashboard.tickersWithoutData()).containsExactly(QQQ);
        assertThat(dashboard.averageScore()).isEqualByComparingTo("0.00");
    }

    @Test
    void anEmptyPortfolioProducesAnEmptyDashboard() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        SignalsDashboard dashboard = useCase.execute();

        assertThat(dashboard.signals()).isEmpty();
        assertThat(dashboard.tickersWithoutData()).isEmpty();
        assertThat(dashboard.averageScore()).isEqualByComparingTo("0.00");
    }
}
