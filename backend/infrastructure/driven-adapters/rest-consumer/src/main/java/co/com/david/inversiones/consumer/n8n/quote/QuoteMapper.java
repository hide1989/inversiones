package co.com.david.inversiones.consumer.n8n.quote;

import co.com.david.inversiones.consumer.n8n.dto.BvcPreciosResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.PreciosHoyResponseDto;
import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Percentage;
import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written mapping from the two n8n price payloads to domain {@link Quote}s.
 *
 * <p>The currency a {@link Quote} carries is decided by which endpoint produced it, never by a
 * field inside the payload and never by a project-wide default: {@link #fromPreciosHoy} always
 * builds {@link Money#usd}, {@link #fromBvcPrecios} always builds {@link Money#cop}. This mirrors
 * how the two endpoints actually work — {@code /precios-hoy} is NYSE/NASDAQ, {@code /bvc-precios}
 * is the Colombian exchange — so hardcoding a single currency for both would misprice every
 * Colombian holding as if its peso amount were dollars.
 */
final class QuoteMapper {

    private static final Logger log = LoggerFactory.getLogger(QuoteMapper.class);

    private QuoteMapper() {
    }

    static Map<Ticker, Quote> fromPreciosHoy(Optional<PreciosHoyResponseDto> response) {
        Map<Ticker, Quote> quotes = new LinkedHashMap<>();
        response.map(PreciosHoyResponseDto::datos).ifPresent(datos -> datos.forEach((symbol, entry) -> {
            toTicker(symbol).ifPresent(ticker -> {
                if (entry.precioActual() != null) {
                    quotes.put(ticker, new Quote(ticker, Money.of(entry.precioActual(), Currency.USD),
                            percentOrZero(entry.cambioPct())));
                }
            });
        }));
        return quotes;
    }

    static Map<Ticker, Quote> fromBvcPrecios(Optional<BvcPreciosResponseDto> response) {
        Map<Ticker, Quote> quotes = new LinkedHashMap<>();
        response.map(BvcPreciosResponseDto::bvc).ifPresent(bvc -> bvc.forEach((symbol, entry) -> {
            toTicker(symbol).ifPresent(ticker -> {
                if (entry.precioActual() != null) {
                    quotes.put(ticker, new Quote(ticker, Money.of(entry.precioActual(), Currency.COP),
                            percentOrZero(entry.cambioDiaPct())));
                }
            });
        }));
        return quotes;
    }

    private static Percentage percentOrZero(BigDecimal value) {
        return value == null ? Percentage.zero() : Percentage.of(value);
    }

    private static Optional<Ticker> toTicker(String symbol) {
        try {
            return Optional.of(Ticker.of(symbol));
        } catch (InvalidTickerException e) {
            log.warn("Skipping quote for unparseable ticker symbol '{}'", symbol);
            return Optional.empty();
        }
    }
}
