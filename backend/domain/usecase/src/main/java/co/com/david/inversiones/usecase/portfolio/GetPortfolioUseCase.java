package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import co.com.david.inversiones.model.gateways.QuoteProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives the current portfolio from the transaction history and values it against market prices.
 *
 * <p>Position identity is owned by {@link Position#deriveAll(java.util.List)}. A position whose
 * quote is unavailable is carried at cost and reported in
 * {@link PortfolioValuation#unpricedTickers()} rather than being dropped or valued at a guess.
 *
 * <p>Positions keep their native amounts. Every portfolio-wide aggregate is expressed in
 * {@link #BASE_CURRENCY}, converted with the TRM obtained from {@link ExchangeRateProvider} only
 * when at least one position is not already denominated in it. When that rate cannot be obtained,
 * a position that needed it to be expressed in {@link #BASE_CURRENCY} is reported unpriced — the
 * same signal used for a missing market quote — rather than valued with a substitute rate; see
 * {@link #isConvertible}.
 */
public class GetPortfolioUseCase {

    /** Currency every portfolio-wide aggregate is expressed in. */
    private static final Currency BASE_CURRENCY = Currency.USD;

    private final TransactionRepository transactionRepository;
    private final QuoteProvider quoteProvider;
    private final ExchangeRateProvider exchangeRateProvider;

    public GetPortfolioUseCase(TransactionRepository transactionRepository, QuoteProvider quoteProvider,
                               ExchangeRateProvider exchangeRateProvider) {
        this.transactionRepository = transactionRepository;
        this.quoteProvider = quoteProvider;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    public PortfolioValuation execute() {
        List<Position> openPositions = Position.deriveAll(transactionRepository.findAll());

        Set<Ticker> tickers = openPositions.stream()
                .map(Position::ticker)
                .collect(Collectors.toUnmodifiableSet());
        Map<Ticker, Quote> quotes = quoteProvider.findQuotes(tickers);

        boolean needsConversion = openPositions.stream()
                .anyMatch(position -> position.totalCost().currency() != BASE_CURRENCY);
        Optional<ExchangeRate> rate = needsConversion
                ? exchangeRateProvider.currentCopPerUsd()
                : Optional.empty();

        List<ValuedPosition> valued = openPositions.stream()
                .map(position -> value(position, quotes.get(position.ticker()), rate))
                .toList();

        Money totalInvested = valued.stream()
                .map(ValuedPosition::costInBaseCurrency)
                .reduce(Money.zero(BASE_CURRENCY), Money::add);
        Money totalMarketValue = valued.stream()
                .map(ValuedPosition::marketValueInBaseCurrency)
                .reduce(Money.zero(BASE_CURRENCY), Money::add);
        Money totalUnrealizedPnl = totalMarketValue.subtract(totalInvested);

        List<Ticker> unpriced = valued.stream()
                .filter(v -> !v.priced())
                .map(v -> v.position().ticker())
                .distinct()
                .toList();

        return PortfolioValuation.builder()
                .positions(valued)
                .totalInvested(totalInvested)
                .totalMarketValue(totalMarketValue)
                .totalUnrealizedPnl(totalUnrealizedPnl)
                .unpricedTickers(unpriced)
                .baseCurrency(BASE_CURRENCY)
                .build();
    }

    private ValuedPosition value(Position position, Quote quote, Optional<ExchangeRate> rate) {
        boolean convertible = isConvertible(position.totalCost(), rate);
        if (quote == null) {
            Money costInBase = convertible
                    ? toBaseCurrency(position.totalCost(), rate)
                    : Money.zero(BASE_CURRENCY);
            return ValuedPosition.builder()
                    .position(position)
                    .marketValue(position.totalCost())
                    .unrealizedPnl(Money.zero(position.totalCost().currency()))
                    .unrealizedPnlPercent(Percentage.zero())
                    .dayChangePercent(Percentage.zero())
                    .priced(false)
                    .costInBaseCurrency(costInBase)
                    .marketValueInBaseCurrency(costInBase)
                    .build();
        }
        Money marketValue = quote.price().multiply(position.quantity().value());
        Money unrealizedPnl = marketValue.subtract(position.totalCost());
        return ValuedPosition.builder()
                .position(position)
                .marketValue(marketValue)
                .unrealizedPnl(unrealizedPnl)
                .unrealizedPnlPercent(Percentage.between(unrealizedPnl, position.totalCost()))
                .dayChangePercent(quote.dayChangePercent())
                .priced(convertible)
                .costInBaseCurrency(convertible
                        ? toBaseCurrency(position.totalCost(), rate)
                        : Money.zero(BASE_CURRENCY))
                .marketValueInBaseCurrency(convertible
                        ? toBaseCurrency(marketValue, rate)
                        : Money.zero(BASE_CURRENCY))
                .build();
    }

    /**
     * Whether {@code amount} can be expressed in {@link #BASE_CURRENCY}: trivially true when it is
     * already in that currency, otherwise only when a rate is available to convert it with.
     *
     * <p>This is the seam where the Critical fix lands. A rate that could not be obtained from
     * {@link ExchangeRateProvider} used to be silently replaced by a fabricated one, so this method
     * — and therefore this whole class — never had to consider the "no rate" case. Now it does:
     * a position that needs a rate to be valued in the base currency and does not have one is
     * exactly like a position with no market quote, and is reported the same honest way, by reusing
     * {@link ValuedPosition#priced()} rather than inventing a second, parallel "unavailable" concept.
     */
    private boolean isConvertible(Money amount, Optional<ExchangeRate> rate) {
        return amount.currency() == BASE_CURRENCY || rate.isPresent();
    }

    /**
     * Converts an amount into {@link #BASE_CURRENCY}.
     *
     * <p>The rate converts USD into COP, so a peso amount is divided by it rather than multiplied.
     * Callers must only invoke this when {@link #isConvertible} already returned {@code true} for
     * the same amount and rate; the arithmetic for a present rate is unchanged from before this
     * class had to consider absence at all.
     */
    private Money toBaseCurrency(Money amount, Optional<ExchangeRate> rate) {
        if (amount.currency() == BASE_CURRENCY) {
            return amount;
        }
        ExchangeRate presentRate = rate.orElseThrow(() -> new IllegalStateException(
                "toBaseCurrency called with no rate for a non-base-currency amount"));
        return Money.of(amount.amount().divide(presentRate.rate(), Money.SCALE, RoundingMode.HALF_UP),
                BASE_CURRENCY);
    }
}
