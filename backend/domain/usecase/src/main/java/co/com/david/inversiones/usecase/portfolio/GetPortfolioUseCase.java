package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.gateways.QuoteProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives the current portfolio from the transaction history and values it against market prices.
 *
 * <p>Position identity is owned by {@link Position#deriveAll(java.util.List)}. A position whose
 * quote is unavailable is carried at cost and reported in
 * {@link PortfolioValuation#unpricedTickers()} rather than being dropped or valued at a guess.
 */
public class GetPortfolioUseCase {

    private final TransactionRepository transactionRepository;
    private final QuoteProvider quoteProvider;

    public GetPortfolioUseCase(TransactionRepository transactionRepository, QuoteProvider quoteProvider) {
        this.transactionRepository = transactionRepository;
        this.quoteProvider = quoteProvider;
    }

    public PortfolioValuation execute() {
        List<Position> openPositions = Position.deriveAll(transactionRepository.findAll());

        Set<Ticker> tickers = openPositions.stream()
                .map(Position::ticker)
                .collect(Collectors.toUnmodifiableSet());
        Map<Ticker, Quote> quotes = quoteProvider.findQuotes(tickers);

        Currency currency = openPositions.isEmpty()
                ? Currency.USD
                : openPositions.getFirst().totalCost().currency();

        List<ValuedPosition> valued = openPositions.stream()
                .map(position -> value(position, quotes.get(position.ticker())))
                .toList();

        Money totalInvested = valued.stream()
                .map(v -> v.position().totalCost())
                .reduce(Money.zero(currency), Money::add);
        Money totalMarketValue = valued.stream()
                .map(ValuedPosition::marketValue)
                .reduce(Money.zero(currency), Money::add);
        Money totalUnrealizedPnl = valued.stream()
                .map(ValuedPosition::unrealizedPnl)
                .reduce(Money.zero(currency), Money::add);

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
                .build();
    }

    private ValuedPosition value(Position position, Quote quote) {
        if (quote == null) {
            return ValuedPosition.builder()
                    .position(position)
                    .marketValue(position.totalCost())
                    .unrealizedPnl(Money.zero(position.totalCost().currency()))
                    .unrealizedPnlPercent(Percentage.zero())
                    .dayChangePercent(Percentage.zero())
                    .priced(false)
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
                .build();
    }
}
