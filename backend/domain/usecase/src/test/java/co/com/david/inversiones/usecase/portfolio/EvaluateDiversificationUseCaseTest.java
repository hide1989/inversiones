package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluateDiversificationUseCaseTest {

    @Mock
    private GetPortfolioUseCase getPortfolio;
    @Mock
    private InstrumentRepository instrumentRepository;
    @InjectMocks
    private EvaluateDiversificationUseCase useCase;

    private static ValuedPosition position(String ticker, String broker, String marketValue) {
        Position held = Position.builder()
                .ticker(Ticker.of(ticker)).brokerName(broker).quantity(Quantity.of("1"))
                .totalCost(Money.usd(marketValue)).averageCost(Money.usd(marketValue))
                .realizedPnl(Money.zero(Currency.USD)).build();
        return ValuedPosition.builder()
                .position(held).marketValue(Money.usd(marketValue))
                .unrealizedPnl(Money.zero(Currency.USD)).unrealizedPnlPercent(Percentage.zero())
                .dayChangePercent(Percentage.zero()).priced(true)
                .costInBaseCurrency(Money.usd(marketValue))
                .marketValueInBaseCurrency(Money.usd(marketValue))
                .build();
    }

    /**
     * Builds a position whose native amount is in {@code nativeCost}'s currency (possibly pesos)
     * while its base-currency market value is supplied explicitly, since the use case reads only
     * the base-currency field.
     */
    private static ValuedPosition positionWithBase(String ticker, String broker, Money nativeCost,
                                                   Money marketValueInBase) {
        Position held = Position.builder()
                .ticker(Ticker.of(ticker)).brokerName(broker).quantity(Quantity.of("1"))
                .totalCost(nativeCost).averageCost(nativeCost)
                .realizedPnl(Money.zero(nativeCost.currency())).build();
        return ValuedPosition.builder()
                .position(held).marketValue(nativeCost)
                .unrealizedPnl(Money.zero(nativeCost.currency())).unrealizedPnlPercent(Percentage.zero())
                .dayChangePercent(Percentage.zero()).priced(true)
                .costInBaseCurrency(marketValueInBase)
                .marketValueInBaseCurrency(marketValueInBase)
                .build();
    }

    private void catalog(String ticker, InstrumentType type, String sector) {
        when(instrumentRepository.findByTicker(Ticker.of(ticker))).thenReturn(Optional.of(
                Instrument.builder().ticker(Ticker.of(ticker)).name(ticker).type(type)
                        .sector(sector).exchange("NASDAQ").currency(Currency.USD).build()));
    }

    private void portfolioOf(ValuedPosition... positions) {
        Money total = List.of(positions).stream().map(ValuedPosition::marketValueInBaseCurrency)
                .reduce(Money.zero(Currency.USD), Money::add);
        when(getPortfolio.execute()).thenReturn(PortfolioValuation.builder()
                .positions(List.of(positions)).totalInvested(total).totalMarketValue(total)
                .totalUnrealizedPnl(Money.zero(Currency.USD)).unpricedTickers(List.of())
                .baseCurrency(Currency.USD).build());
    }

    @Test
    void reportsCompliantWhenEveryWeightIsWithinItsLimit() {
        // Weights: QQQ 25 (ETF, limit 25), TSLA 19, JPM 19, MSFT 19, LLY 18 (limit 20 each).
        // Sectors: Broad Market 25, Consumer Discretionary 19, Financials 19, Technology 19,
        // Health Care 18 (limit 30 each). Total market value 100.
        portfolioOf(position("QQQ", "Hapi", "25"), position("TSLA", "Hapi", "19"),
                position("JPM", "ARQ", "19"), position("MSFT", "ARQ", "19"),
                position("LLY", "ARQ", "18"));
        catalog("QQQ", InstrumentType.ETF, "Broad Market");
        catalog("TSLA", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("JPM", InstrumentType.EQUITY, "Financials");
        catalog("MSFT", InstrumentType.EQUITY, "Technology");
        catalog("LLY", InstrumentType.EQUITY, "Health Care");

        DiversificationReport report = useCase.execute();

        assertThat(report.compliant()).isTrue();
        assertThat(report.violations()).isEmpty();
        assertThat(report.weightByTicker().get("QQQ").value()).isEqualByComparingTo("25.0000");
    }

    @Test
    void flagsASingleStockAboveTwentyPercent() {
        portfolioOf(position("TSLA", "Hapi", "30"), position("QQQ", "Hapi", "70"));
        catalog("TSLA", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.violations()).anySatisfy(violation -> {
            assertThat(violation.limit()).isEqualTo(DiversificationLimit.SINGLE_EQUITY);
            assertThat(violation.subject()).isEqualTo("TSLA");
            assertThat(violation.actualWeight().value()).isEqualByComparingTo("30.0000");
            assertThat(violation.allowedWeight().value()).isEqualByComparingTo("20.0000");
        });
    }

    @Test
    void allowsAnEtfUpToTwentyFivePercent() {
        portfolioOf(position("QQQ", "Hapi", "25"), position("JPM", "Hapi", "15"),
                position("TSLA", "Hapi", "15"), position("MSFT", "Hapi", "15"),
                position("AMZN", "Hapi", "15"), position("NU", "Hapi", "15"));
        catalog("QQQ", InstrumentType.ETF, "Broad Market");
        catalog("JPM", InstrumentType.EQUITY, "Financials");
        catalog("TSLA", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("MSFT", InstrumentType.EQUITY, "Technology");
        catalog("AMZN", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("NU", InstrumentType.EQUITY, "Financials");

        DiversificationReport report = useCase.execute();

        assertThat(report.violations()).extracting(DiversificationViolation::subject)
                .doesNotContain("QQQ");
    }

    @Test
    void addsTheSameTickerAcrossBrokersIntoOneExposure() {
        portfolioOf(position("TSLA", "Hapi", "15"), position("TSLA", "ARQ", "15"),
                position("QQQ", "Hapi", "70"));
        catalog("TSLA", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.weightByTicker().get("TSLA").value()).isEqualByComparingTo("30.0000");
        assertThat(report.violations()).extracting(DiversificationViolation::subject).contains("TSLA");
    }

    @Test
    void flagsASectorAboveThirtyPercent() {
        portfolioOf(position("TSLA", "Hapi", "18"), position("AMZN", "Hapi", "18"),
                position("QQQ", "Hapi", "64"));
        catalog("TSLA", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("AMZN", InstrumentType.EQUITY, "Consumer Discretionary");
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.violations()).anySatisfy(violation -> {
            assertThat(violation.limit()).isEqualTo(DiversificationLimit.SECTOR);
            assertThat(violation.subject()).isEqualTo("Consumer Discretionary");
            assertThat(violation.actualWeight().value()).isEqualByComparingTo("36.0000");
        });
    }

    @Test
    void treatsAnUncatalogedInstrumentAsAnEquityInAnUnknownSector() {
        portfolioOf(position("SPCX", "ARQ", "30"), position("QQQ", "Hapi", "70"));
        when(instrumentRepository.findByTicker(Ticker.of("SPCX"))).thenReturn(Optional.empty());
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.violations()).anySatisfy(violation -> {
            assertThat(violation.limit()).isEqualTo(DiversificationLimit.SINGLE_EQUITY);
            assertThat(violation.subject()).isEqualTo("SPCX");
        });
        assertThat(report.weightBySector()).containsKey("UNKNOWN");
    }

    @Test
    void weighsPesoAndDollarHoldingsOnTheSameBaseCurrencyScale() {
        // TERPEL: 30 USD of base-currency market value; QQQ: 70 USD. Total 100.
        portfolioOf(positionWithBase("TERPEL", "Trii", Money.cop("120000"), Money.usd("30")),
                positionWithBase("QQQ", "Hapi", Money.usd("70"), Money.usd("70")));
        catalog("TERPEL", InstrumentType.EQUITY, "Energy");
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.weightByTicker().get("TERPEL").value()).isEqualByComparingTo("30.0000");
        assertThat(report.violations()).extracting(DiversificationViolation::subject).contains("TERPEL");
    }

    @Test
    void anEmptyPortfolioIsCompliantWithNoWeights() {
        portfolioOf();

        DiversificationReport report = useCase.execute();

        assertThat(report.compliant()).isTrue();
        assertThat(report.weightByTicker()).isEmpty();
        assertThat(report.weightBySector()).isEmpty();
    }
}
