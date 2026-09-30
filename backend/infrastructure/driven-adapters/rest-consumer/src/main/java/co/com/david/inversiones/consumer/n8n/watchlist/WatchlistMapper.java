package co.com.david.inversiones.consumer.n8n.watchlist;

import co.com.david.inversiones.consumer.n8n.dto.WatchlistResponseDto;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.WatchlistItem;
import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** Maps {@code /watchlist} entries into {@link WatchlistItem}. */
final class WatchlistMapper {

    private static final Logger log = LoggerFactory.getLogger(WatchlistMapper.class);

    private WatchlistMapper() {
    }

    static List<WatchlistItem> fromWatchlist(Optional<WatchlistResponseDto> response) {
        return response
                .map(dto -> {
                    List<WatchlistResponseDto.Entry> entries = dto.watchlist();
                    if (entries == null) {
                        return List.<WatchlistItem>of();
                    }
                    return entries.stream()
                            .map(entry -> toTicker(entry.ticker()).map(ticker -> toItem(ticker, entry)))
                            .flatMap(Optional::stream)
                            .toList();
                })
                .orElseGet(List::of);
    }

    private static WatchlistItem toItem(Ticker ticker, WatchlistResponseDto.Entry entry) {
        return WatchlistItem.builder()
                .ticker(ticker)
                .price(entry.precio())
                .technicalScore(entry.scoreTecnico())
                .wsbScore(entry.scoreSocial())
                .combinedScore(entry.scoreTotal())
                .signal(entry.senal())
                .build();
    }

    private static Optional<Ticker> toTicker(String symbol) {
        try {
            return Optional.of(Ticker.of(symbol));
        } catch (InvalidTickerException e) {
            log.warn("Skipping watchlist row for unparseable ticker symbol '{}'", symbol);
            return Optional.empty();
        }
    }
}
