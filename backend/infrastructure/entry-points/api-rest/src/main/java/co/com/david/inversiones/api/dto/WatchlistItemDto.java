package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.WatchlistItem;

/** Wire representation of a {@link WatchlistItem}. Several fields are frequently absent upstream. */
public record WatchlistItemDto(
        String ticker, String price, String technicalScore, String wsbScore, String combinedScore, String signal) {

    public static WatchlistItemDto from(WatchlistItem item) {
        return new WatchlistItemDto(
                item.ticker().symbol(),
                DecimalStrings.of(item.price()),
                DecimalStrings.of(item.technicalScore()),
                DecimalStrings.of(item.wsbScore()),
                DecimalStrings.of(item.combinedScore()),
                item.signal());
    }
}
