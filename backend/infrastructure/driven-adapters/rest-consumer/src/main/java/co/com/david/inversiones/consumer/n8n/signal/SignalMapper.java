package co.com.david.inversiones.consumer.n8n.signal;

import co.com.david.inversiones.consumer.n8n.dto.IndicadoresTecnicosResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.InsiderTradingResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.NewsSentimentResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.RatingsAnalistasResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.RedditResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.StocktwitsResponseDto;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extracts one {@code (ticker -> score)} map per signal endpoint.
 *
 * <p>Every extractor is total: a missing or malformed response ({@link Optional#empty()}, a
 * {@code null} list, or a {@code null} score for a given ticker) simply contributes no entries or
 * skips that entry — it never throws. This is what lets {@code SignalProviderAdapter} treat "the
 * endpoint failed" and "the endpoint returned but had nothing to say about this ticker" the same
 * way: both leave that source absent from the snapshot, never present with a fabricated zero.
 */
final class SignalMapper {

    private static final Logger log = LoggerFactory.getLogger(SignalMapper.class);

    private SignalMapper() {
    }

    static Map<Ticker, BigDecimal> fromIndicadoresTecnicos(Optional<IndicadoresTecnicosResponseDto> response) {
        return extract(response.map(IndicadoresTecnicosResponseDto::tickers),
                IndicadoresTecnicosResponseDto.Entry::ticker, IndicadoresTecnicosResponseDto.Entry::scoreTecnico);
    }

    static Map<Ticker, BigDecimal> fromNewsSentiment(Optional<NewsSentimentResponseDto> response) {
        return extract(response.map(NewsSentimentResponseDto::tickers),
                NewsSentimentResponseDto.Entry::ticker, NewsSentimentResponseDto.Entry::scoreNoticias);
    }

    static Map<Ticker, BigDecimal> fromRatingsAnalistas(Optional<RatingsAnalistasResponseDto> response) {
        return extract(response.map(RatingsAnalistasResponseDto::tickers),
                RatingsAnalistasResponseDto.Entry::ticker, RatingsAnalistasResponseDto.Entry::ratingScore);
    }

    static Map<Ticker, BigDecimal> fromInsiderTrading(Optional<InsiderTradingResponseDto> response) {
        return extract(response.map(InsiderTradingResponseDto::insiders),
                InsiderTradingResponseDto.Entry::ticker, InsiderTradingResponseDto.Entry::insiderScore);
    }

    static Map<Ticker, BigDecimal> fromStocktwits(Optional<StocktwitsResponseDto> response) {
        return extract(response.map(StocktwitsResponseDto::tickers),
                StocktwitsResponseDto.Entry::ticker, StocktwitsResponseDto.Entry::scoreSocial);
    }

    static Map<Ticker, BigDecimal> fromReddit(Optional<RedditResponseDto> response) {
        return extract(response.map(RedditResponseDto::tickers),
                RedditResponseDto.Entry::ticker, RedditResponseDto.Entry::scoreReddit);
    }

    private static <E> Map<Ticker, BigDecimal> extract(Optional<List<E>> entries,
            Function<E, String> tickerOf, Function<E, BigDecimal> scoreOf) {
        Map<Ticker, BigDecimal> result = new LinkedHashMap<>();
        entries.ifPresent(list -> list.forEach(entry -> {
            BigDecimal score = scoreOf.apply(entry);
            if (score == null) {
                return;
            }
            toTicker(tickerOf.apply(entry)).ifPresent(ticker -> result.put(ticker, score));
        }));
        return result;
    }

    private static Optional<Ticker> toTicker(String symbol) {
        try {
            return Optional.of(Ticker.of(symbol));
        } catch (InvalidTickerException e) {
            log.warn("Skipping signal for unparseable ticker symbol '{}'", symbol);
            return Optional.empty();
        }
    }
}
