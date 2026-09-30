package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import co.com.david.inversiones.model.gateways.InstrumentRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reports the portfolio return in Colombian pesos, keeping the asset gain and the exchange rate
 * effect separate.
 *
 * <p>The investor buys in pesos and holds in dollars, so a dollar-denominated gain and a peso
 * devaluation gain are different things. Collapsing them into one number hides which of the two
 * actually produced the result, so they are reported side by side.
 */
public class CalculateTotalReturnUseCase {

    private static final String TRII = "Trii";
    private static final String BVC_EXCHANGE = "BVC";

    private final GetPortfolioUseCase getPortfolio;
    private final InstrumentRepository instrumentRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final BigDecimal baseTrm;

    public CalculateTotalReturnUseCase(GetPortfolioUseCase getPortfolio,
                                       InstrumentRepository instrumentRepository,
                                       ExchangeRateProvider exchangeRateProvider,
                                       BigDecimal baseTrm) {
        this.getPortfolio = getPortfolio;
        this.instrumentRepository = instrumentRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.baseTrm = baseTrm;
    }

    public TotalReturn execute() {
        PortfolioValuation valuation = getPortfolio.execute();
        ExchangeRate currentRate = exchangeRateProvider.currentCopPerUsd();

        Map<MarketSegment, List<ValuedPosition>> grouped = new LinkedHashMap<>();
        for (MarketSegment segment : MarketSegment.values()) {
            grouped.put(segment, new ArrayList<>());
        }
        for (ValuedPosition position : valuation.positions()) {
            grouped.get(segmentOf(position)).add(position);
        }

        List<ReturnSegment> segments = grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> summarize(entry.getKey(), entry.getValue(), currentRate))
                .toList();

        Set<MarketSegment> dollarSegments = EnumSet.of(MarketSegment.USD_ASSETS, MarketSegment.TRII_ADR);
        Set<MarketSegment> pesoSegments = EnumSet.of(MarketSegment.BVC_COLOMBIA);

        Money assetGainUsd = sum(segments, dollarSegments, ReturnSegment::assetGain, Currency.USD);
        Money investedUsd = sum(segments, dollarSegments, ReturnSegment::invested, Currency.USD);
        Money assetGainCop = sum(segments, pesoSegments, ReturnSegment::assetGain, Currency.COP);

        Money assetGainInCop = currentRate.convert(assetGainUsd).add(assetGainCop);
        Money exchangeEffect = Money.of(
                investedUsd.amount().multiply(currentRate.rate().subtract(baseTrm)), Currency.COP);

        return TotalReturn.builder()
                .segments(segments)
                .baseRate(ExchangeRate.copPerUsd(baseTrm, currentRate.rateDate()))
                .currentRate(currentRate)
                .exchangeEffect(exchangeEffect)
                .assetGainInCop(assetGainInCop)
                .totalReturnInCop(assetGainInCop.add(exchangeEffect))
                .build();
    }

    private MarketSegment segmentOf(ValuedPosition position) {
        if (!TRII.equals(position.position().brokerName())) {
            return MarketSegment.USD_ASSETS;
        }
        boolean onBvc = instrumentRepository.findByTicker(position.position().ticker())
                .map(instrument -> BVC_EXCHANGE.equals(instrument.exchange()))
                .orElse(false);
        return onBvc ? MarketSegment.BVC_COLOMBIA : MarketSegment.TRII_ADR;
    }

    private ReturnSegment summarize(MarketSegment segment, List<ValuedPosition> positions, ExchangeRate rate) {
        Currency currency = segment.currency();
        Money invested = positions.stream()
                .map(position -> toSegmentCurrency(position.position().totalCost(), currency, rate))
                .reduce(Money.zero(currency), Money::add);
        Money marketValue = positions.stream()
                .map(position -> toSegmentCurrency(position.marketValue(), currency, rate))
                .reduce(Money.zero(currency), Money::add);
        return ReturnSegment.builder()
                .segment(segment)
                .invested(invested)
                .marketValue(marketValue)
                .assetGain(marketValue.subtract(invested))
                .build();
    }

    /**
     * Returns {@code amount} unchanged when it already matches {@code segmentCurrency}, or converts
     * it with {@code rate} otherwise. A currency that still disagrees after conversion is a
     * programming error, so no exception is swallowed here: the subsequent {@link Money#add} call
     * raises {@code CurrencyMismatchException} instead of papering over it.
     */
    private Money toSegmentCurrency(Money amount, Currency segmentCurrency, ExchangeRate rate) {
        if (amount.currency() == segmentCurrency) {
            return amount;
        }
        return rate.convert(amount);
    }

    private Money sum(List<ReturnSegment> segments, Set<MarketSegment> included,
                      Function<ReturnSegment, Money> field, Currency currency) {
        return segments.stream()
                .filter(segment -> included.contains(segment.segment()))
                .map(field)
                .reduce(Money.zero(currency), Money::add);
    }
}
