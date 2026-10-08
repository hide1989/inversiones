package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Quantity;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.TransactionType;
import co.com.david.inversiones.model.gateways.AiSynthesisProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAiSynthesisUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);
    private static final Ticker NVDA = Ticker.of("NVDA");
    private static final Ticker MSFT = Ticker.of("MSFT");
    private static final Ticker TSLA = Ticker.of("TSLA");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AiSynthesisProvider synthesisProvider;
    @Captor
    private ArgumentCaptor<Set<Ticker>> tickersCaptor;
    @InjectMocks
    private GetAiSynthesisUseCase useCase;

    private static Transaction holding(Ticker ticker) {
        return Transaction.builder()
                .ticker(ticker).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(DATE.minusMonths(3)).build();
    }

    private static AiSynthesis withData(Ticker ticker, String score) {
        return AiSynthesis.builder()
                .ticker(ticker).snapshotDate(DATE).iaScore(new BigDecimal(score))
                .signal("COMPRA").conviction("alta").horizon("3-6 meses").summary("...")
                .hasData(true).build();
    }

    private static AiSynthesis withoutData(Ticker ticker) {
        return AiSynthesis.builder()
                .ticker(ticker).snapshotDate(DATE).iaScore(new BigDecimal("5"))
                .signal("MANTENER").conviction("media").horizon("3-6 meses")
                .summary("Sin analisis disponible: la IA no devolvio una respuesta valida.")
                .hasData(false).build();
    }

    @Test
    void aSinDatosTickerIsExcludedFromTheAverageAndListedSeparately() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(NVDA), holding(MSFT), holding(TSLA)));
        when(synthesisProvider.fetchSyntheses(any())).thenReturn(List.of(
                withData(NVDA, "8"), withData(MSFT, "7"), withoutData(TSLA)));

        AiSynthesisReport report = useCase.execute();

        // Average must come from exactly the two tickers with data: (8 + 7) / 2 = 7.50.
        assertThat(report.averageScore()).isEqualByComparingTo("7.50");
        assertThat(report.withData()).extracting(s -> s.ticker().symbol()).containsExactlyInAnyOrder("NVDA", "MSFT");
        assertThat(report.withoutData()).containsExactly(TSLA);
    }

    @Test
    void queriesTheProviderForExactlyTheHeldTickers() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(NVDA)));
        when(synthesisProvider.fetchSyntheses(tickersCaptor.capture())).thenReturn(List.of());

        useCase.execute();

        assertThat(tickersCaptor.getValue()).containsExactly(NVDA);
    }

    @Test
    void anEmptyPortfolioProducesAnEmptyReportWithoutCallingTheProvider() {
        when(transactionRepository.findAll()).thenReturn(List.of());
        when(synthesisProvider.fetchSyntheses(Set.of())).thenReturn(List.of());

        AiSynthesisReport report = useCase.execute();

        assertThat(report.withData()).isEmpty();
        assertThat(report.withoutData()).isEmpty();
        assertThat(report.averageScore()).isEqualByComparingTo("0.00");
    }

    @Test
    void allTickersWithoutDataProduceAZeroAverageRatherThanADivisionByZero() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(TSLA)));
        when(synthesisProvider.fetchSyntheses(any())).thenReturn(List.of(withoutData(TSLA)));

        AiSynthesisReport report = useCase.execute();

        assertThat(report.withData()).isEmpty();
        assertThat(report.withoutData()).containsExactly(TSLA);
        assertThat(report.averageScore()).isEqualByComparingTo("0.00");
    }

    /**
     * Live-contract regression: n8n can mark an entry {@code sin_datos: false} while still
     * sending a null {@code score_ia} (a partially-failed LLM call). Before the fix, {@code
     * hasData} was trusted verbatim from the upstream flag, so this entry landed in {@code
     * withData} and {@link GetAiSynthesisUseCase#averageOf} threw a {@link NullPointerException}
     * summing a null {@link BigDecimal} — the exact HTTP 500 the final review found on
     * {@code /sintesis-ia}. A ticker whose score is genuinely absent must be excluded from
     * {@code withData} regardless of what {@code hasData} claims.
     */
    @Test
    void aTickerMarkedHasDataButWithANullScoreIsExcludedRatherThanThrowing() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(NVDA), holding(MSFT)));
        AiSynthesis corrupted = AiSynthesis.builder()
                .ticker(NVDA).snapshotDate(DATE).iaScore(null)
                .signal("COMPRA").conviction("alta").horizon("3-6 meses").summary("...")
                .hasData(true).build();
        when(synthesisProvider.fetchSyntheses(any())).thenReturn(List.of(corrupted, withData(MSFT, "6")));

        AiSynthesisReport report = useCase.execute();

        assertThat(report.withData()).extracting(s -> s.ticker().symbol()).containsExactly("MSFT");
        assertThat(report.withoutData()).containsExactly(NVDA);
        assertThat(report.averageScore()).isEqualByComparingTo("6.00");
    }
}
