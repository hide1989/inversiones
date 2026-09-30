package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateTotalReturnUseCaseTest {

    private static final BigDecimal BASE_TRM = new BigDecimal("3568.42");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final ExchangeRate CURRENT = ExchangeRate.copPerUsd(new BigDecimal("3912.40"), TODAY);

    @Mock
    private GetPortfolioUseCase getPortfolio;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    private CalculateTotalReturnUseCase useCase() {
        return new CalculateTotalReturnUseCase(
                getPortfolio, instrumentRepository, exchangeRateProvider, BASE_TRM);
    }

    private static ValuedPosition position(String ticker, String broker, String cost, String marketValue) {
        Position held = Position.builder()
                .ticker(Ticker.of(ticker)).brokerName(broker)
                .quantity(Quantity.of("1")).totalCost(Money.usd(cost))
                .averageCost(Money.usd(cost)).realizedPnl(Money.zero(Currency.USD))
                .build();
        return ValuedPosition.builder()
                .position(held).marketValue(Money.usd(marketValue))
                .unrealizedPnl(Money.usd(marketValue).subtract(Money.usd(cost)))
                .unrealizedPnlPercent(Percentage.zero()).dayChangePercent(Percentage.zero())
                .priced(true)
                .costInBaseCurrency(Money.usd(cost))
                .marketValueInBaseCurrency(Money.usd(marketValue))
                .build();
    }

    /**
     * Builds a position natively denominated in Colombian pesos, matching the currency the BVC
     * segment reports in. The base-currency fields are unused by this use case; they are set to
     * arbitrary non-null dollar amounts only to satisfy {@link ValuedPosition}'s required fields.
     */
    private static ValuedPosition positionCop(String ticker, String broker, String cost, String marketValue) {
        Position held = Position.builder()
                .ticker(Ticker.of(ticker)).brokerName(broker)
                .quantity(Quantity.of("1")).totalCost(Money.cop(cost))
                .averageCost(Money.cop(cost)).realizedPnl(Money.zero(Currency.COP))
                .build();
        return ValuedPosition.builder()
                .position(held).marketValue(Money.cop(marketValue))
                .unrealizedPnl(Money.cop(marketValue).subtract(Money.cop(cost)))
                .unrealizedPnlPercent(Percentage.zero()).dayChangePercent(Percentage.zero())
                .priced(true)
                .costInBaseCurrency(Money.usd(cost))
                .marketValueInBaseCurrency(Money.usd(marketValue))
                .build();
    }

    private void catalog(String ticker, String exchange) {
        when(instrumentRepository.findByTicker(Ticker.of(ticker))).thenReturn(Optional.of(
                Instrument.builder().ticker(Ticker.of(ticker)).name(ticker)
                        .type(InstrumentType.EQUITY).sector("Any").exchange(exchange)
                        .currency(Currency.USD).build()));
    }

    private void portfolioOf(ValuedPosition... positions) {
        when(getPortfolio.execute()).thenReturn(PortfolioValuation.builder()
                .positions(List.of(positions))
                .totalInvested(Money.usd("0")).totalMarketValue(Money.usd("0"))
                .totalUnrealizedPnl(Money.usd("0")).unpricedTickers(List.of())
                .baseCurrency(Currency.USD)
                .build());
        when(exchangeRateProvider.currentCopPerUsd()).thenReturn(CURRENT);
    }

    // Note on stubbing: `segmentOf` consults the instrument catalog ONLY for positions held at
    // Trii, short-circuiting everything else to USD_ASSETS. So a test whose positions are all at
    // other brokers must not stub the catalog at all — Mockito's strict stubs would fail it.
    @Test
    void reportsTheAssetGainAndTheExchangeEffectAsSeparateAmounts() {
        portfolioOf(position("QQQ", "Hapi", "300.00", "400.00"));

        TotalReturn result = useCase().execute();

        // Asset gain: 100 USD x 3912.40
        assertThat(result.assetGainInCop().amount()).isEqualByComparingTo("391240");
        // Exchange effect: 300 USD invested x (3912.40 - 3568.42)
        assertThat(result.exchangeEffect().amount()).isEqualByComparingTo("103194");
        assertThat(result.totalReturnInCop().amount()).isEqualByComparingTo("494434");
        assertThat(result.baseRate().rate()).isEqualByComparingTo(BASE_TRM);
        assertThat(result.currentRate()).isEqualTo(CURRENT);
    }

    @Test
    void separatesUsdAssetsFromTriiAdrAndFromTheColombianExchange() {
        portfolioOf(
                position("QQQ", "Hapi", "300.00", "400.00"),
                position("EC", "Trii", "590.66", "600.00"),
                position("TERPEL", "Trii", "100.00", "130.00"));
        catalog("EC", "NYSE");
        catalog("TERPEL", "BVC");

        TotalReturn result = useCase().execute();

        assertThat(result.segments()).extracting(ReturnSegment::segment)
                .containsExactly(MarketSegment.USD_ASSETS, MarketSegment.TRII_ADR, MarketSegment.BVC_COLOMBIA);
        assertThat(result.segments().getFirst().assetGain().amount()).isEqualByComparingTo("100");
    }

    @Test
    void theColombianSegmentContributesNothingToTheExchangeEffect() {
        portfolioOf(positionCop("TERPEL", "Trii", "100.00", "130.00"));
        catalog("TERPEL", "BVC");

        TotalReturn result = useCase().execute();

        assertThat(result.exchangeEffect().isZero()).isTrue();
        assertThat(result.totalReturnInCop().amount()).isEqualByComparingTo("30");
    }

    @Test
    void aFallingExchangeRateProducesANegativeExchangeEffect() {
        when(exchangeRateProvider.currentCopPerUsd())
                .thenReturn(ExchangeRate.copPerUsd(new BigDecimal("3400.00"), TODAY));
        when(getPortfolio.execute()).thenReturn(PortfolioValuation.builder()
                .positions(List.of(position("QQQ", "Hapi", "300.00", "300.00")))
                .totalInvested(Money.usd("300")).totalMarketValue(Money.usd("300"))
                .totalUnrealizedPnl(Money.usd("0")).unpricedTickers(List.of())
                .baseCurrency(Currency.USD)
                .build());

        TotalReturn result = useCase().execute();

        // 300 x (3400.00 - 3568.42)
        assertThat(result.exchangeEffect().amount()).isEqualByComparingTo("-50526");
        assertThat(result.exchangeEffect().isNegative()).isTrue();
    }

    @Test
    void treatsAnUncatalogedInstrumentHeldAtTriiAsAnAdrRatherThanColombian() {
        // The catalog IS consulted here because the broker is Trii; with no entry, the position
        // cannot be shown to trade on the BVC, so it stays dollar-denominated.
        portfolioOf(position("SPCX", "Trii", "150.00", "160.00"));
        when(instrumentRepository.findByTicker(Ticker.of("SPCX"))).thenReturn(Optional.empty());

        TotalReturn result = useCase().execute();

        assertThat(result.segments()).extracting(ReturnSegment::segment)
                .containsExactly(MarketSegment.TRII_ADR);
    }

    @Test
    void treatsAnyPositionOutsideTriiAsAUsdAssetWithoutConsultingTheCatalog() {
        portfolioOf(position("SPCX", "ARQ", "150.00", "160.00"));

        TotalReturn result = useCase().execute();

        assertThat(result.segments()).extracting(ReturnSegment::segment)
                .containsExactly(MarketSegment.USD_ASSETS);
        verifyNoInteractions(instrumentRepository);
    }

    @Test
    void anEmptyPortfolioReturnsZeroEverywhere() {
        portfolioOf();

        TotalReturn result = useCase().execute();

        assertThat(result.segments()).isEmpty();
        assertThat(result.assetGainInCop().isZero()).isTrue();
        assertThat(result.exchangeEffect().isZero()).isTrue();
        assertThat(result.totalReturnInCop().isZero()).isTrue();
    }
}
