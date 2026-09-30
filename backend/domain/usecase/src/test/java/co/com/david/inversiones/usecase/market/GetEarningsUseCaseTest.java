package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Quantity;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.TransactionType;
import co.com.david.inversiones.model.gateways.EarningsProvider;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Days-remaining must come from an injected {@link TimeProvider}, never {@code LocalDate.now()}, so
 * every date in this test is fixed and no assertion depends on when the suite actually runs.
 */
@ExtendWith(MockitoExtension.class)
class GetEarningsUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Ticker TSLA = Ticker.of("TSLA");
    private static final Ticker MSFT = Ticker.of("MSFT");
    private static final Ticker GOOGL = Ticker.of("GOOGL");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private EarningsProvider earningsProvider;
    @Mock
    private TimeProvider timeProvider;
    @InjectMocks
    private GetEarningsUseCase useCase;

    private static Transaction holding(Ticker ticker) {
        return Transaction.builder()
                .ticker(ticker).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(TODAY.minusMonths(3)).build();
    }

    private static EarningsEvent event(Ticker ticker, LocalDate reportDate) {
        return EarningsEvent.builder()
                .ticker(ticker).reportDate(reportDate).daysRemaining(0)
                .estimatedEps(new BigDecimal("1.00")).historicalSurprise(new BigDecimal("5.0"))
                .build();
    }

    @Test
    void computesDaysRemainingFromTheInjectedTimeProviderNotTheSystemClock() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(TSLA)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(earningsProvider.fetchEvents(any())).thenReturn(List.of(event(TSLA, LocalDate.of(2026, 10, 12))));

        List<EarningsEvent> result = useCase.execute();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().daysRemaining()).isEqualTo(12);
    }

    @Test
    void ordersEventsByProximityToTodayAscending() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(TSLA), holding(MSFT)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(earningsProvider.fetchEvents(any())).thenReturn(List.of(
                event(MSFT, LocalDate.of(2026, 11, 20)),
                event(TSLA, LocalDate.of(2026, 10, 12))));

        List<EarningsEvent> result = useCase.execute();

        assertThat(result).extracting(e -> e.ticker().symbol()).containsExactly("TSLA", "MSFT");
    }

    @Test
    void excludesAnEventWhoseReportDateHasAlreadyPassed() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(TSLA), holding(GOOGL)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(earningsProvider.fetchEvents(any())).thenReturn(List.of(
                event(TSLA, LocalDate.of(2026, 10, 12)),
                event(GOOGL, LocalDate.of(2026, 9, 20))));

        List<EarningsEvent> result = useCase.execute();

        assertThat(result).extracting(e -> e.ticker().symbol()).containsExactly("TSLA");
    }

    @Test
    void aTickerWithNoUpcomingEventIsSimplyAbsent() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(TSLA)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(earningsProvider.fetchEvents(any())).thenReturn(List.of());

        List<EarningsEvent> result = useCase.execute();

        assertThat(result).isEmpty();
    }
}
