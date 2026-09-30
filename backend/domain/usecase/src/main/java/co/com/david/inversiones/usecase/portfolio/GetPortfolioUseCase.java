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
 * when at least one position is not already denominated in it.
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
        ExchangeRate rate = needsConversion ? exchangeRateProvider.currentCopPerUsd() : null;

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

    private ValuedPosition value(Position position, Quote quote, ExchangeRate rate) {
        if (quote == null) {
            Money costInBase = toBaseCurrency(position.totalCost(), rate);
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
                .priced(true)
                .costInBaseCurrency(toBaseCurrency(position.totalCost(), rate))
                .marketValueInBaseCurrency(toBaseCurrency(marketValue, rate))
                .build();
    }

    /**
     * Converts an amount into {@link #BASE_CURRENCY}.
     *
     * <p>The rate converts USD into COP, so a peso amount is divided by it rather than multiplied.
     */
    private Money toBaseCurrency(Money amount, ExchangeRate rate) {
        if (amount.currency() == BASE_CURRENCY) {
            return amount;
        }
        return Money.of(amount.amount().divide(rate.rate(), Money.SCALE, RoundingMode.HALF_UP),
                BASE_CURRENCY);
    }
}
