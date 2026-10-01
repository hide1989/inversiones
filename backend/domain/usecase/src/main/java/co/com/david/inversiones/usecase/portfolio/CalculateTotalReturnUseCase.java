package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reports the portfolio return in Colombian pesos, keeping the asset gain and the exchange rate
 * effect separate.
 *
 * <p>The investor buys in pesos and holds in dollars, so a dollar-denominated gain and a peso
 * devaluation gain are different things. Collapsing them into one number hides which of the two
 * actually produced the result, so they are reported side by side.
 *
 * <p><b>When {@code /trm-hoy} is down.</b> {@link ExchangeRateProvider#currentCopPerUsd()} can
 * return {@link Optional#empty()}. Every rate-dependent figure this use case would normally
 * report — {@link TotalReturn#currentRate()}, {@link TotalReturn#exchangeEffect()},
 * {@link TotalReturn#assetGainInCop()} and {@link TotalReturn#totalReturnInCop()} — is then also
 * absent, never zero: an unknown rate must never render as "no exchange-rate effect", which is
 * exactly the silent-wrong-number defect this use case exists to prevent in the first place. The
 * per-segment breakdown ({@link #segments()}) is unaffected, since it is computed in each
 * segment's own native currency and never needs today's rate in practice.
 */
public class CalculateTotalReturnUseCase {

    private static final String TRII = "Trii";
    private static final String BVC_EXCHANGE = "BVC";

    private final GetPortfolioUseCase getPortfolio;
    private final InstrumentRepository instrumentRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final TimeProvider timeProvider;
    private final BigDecimal baseTrm;

    public CalculateTotalReturnUseCase(GetPortfolioUseCase getPortfolio,
                                       InstrumentRepository instrumentRepository,
                                       ExchangeRateProvider exchangeRateProvider,
                                       TimeProvider timeProvider,
                                       BigDecimal baseTrm) {
        this.getPortfolio = getPortfolio;
        this.instrumentRepository = instrumentRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.timeProvider = timeProvider;
        this.baseTrm = baseTrm;
    }

    public TotalReturn execute() {
        PortfolioValuation valuation = getPortfolio.execute();
        Optional<ExchangeRate> currentRate = exchangeRateProvider.currentCopPerUsd();

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

        Optional<Money> assetGainInCop = currentRate.map(rate -> rate.convert(assetGainUsd).add(assetGainCop));
        Optional<Money> exchangeEffect = currentRate.map(rate -> Money.of(
                investedUsd.amount().multiply(rate.rate().subtract(baseTrm)), Currency.COP));
        Optional<Money> totalReturnInCop = assetGainInCop.flatMap(gain -> exchangeEffect.map(gain::add));

        // The base rate is a configured constant; only its reported date depends on today's rate
        // being available at all. When it is not, timeProvider.today() dates it instead — the base
        // rate itself is never fabricated or adjusted, only the date it is stamped with.
        LocalDate baseRateDate = currentRate.map(ExchangeRate::rateDate).orElseGet(timeProvider::today);

        return TotalReturn.builder()
                .segments(segments)
                .baseRate(ExchangeRate.copPerUsd(baseTrm, baseRateDate))
                .currentRate(currentRate)
                .exchangeEffect(exchangeEffect)
                .assetGainInCop(assetGainInCop)
                .totalReturnInCop(totalReturnInCop)
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

    private ReturnSegment summarize(MarketSegment segment, List<ValuedPosition> positions,
                                    Optional<ExchangeRate> rate) {
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
     *
     * <p>In practice a position's native currency always matches its own segment's currency (see
     * {@link MarketSegment}), so the conversion branch is defensive dead code today — which is also
     * why an absent rate here throws rather than silently degrading: this path is not expected to
     * run at all, so it must fail loudly rather than gain a second, untested "no rate" behavior.
     */
    private Money toSegmentCurrency(Money amount, Currency segmentCurrency, Optional<ExchangeRate> rate) {
        if (amount.currency() == segmentCurrency) {
            return amount;
        }
        return rate.orElseThrow(() -> new IllegalStateException(
                "cross-currency segment conversion required but no exchange rate is available"))
                .convert(amount);
    }

    private Money sum(List<ReturnSegment> segments, Set<MarketSegment> included,
                      Function<ReturnSegment, Money> field, Currency currency) {
        return segments.stream()
                .filter(segment -> included.contains(segment.segment()))
                .map(field)
                .reduce(Money.zero(currency), Money::add);
    }
}
