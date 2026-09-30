package co.com.david.inversiones.consumer.n8n.earnings;

import co.com.david.inversiones.consumer.n8n.dto.EarningsResponseDto;
import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Maps {@code /earnings} entries into {@link EarningsEvent}.
 *
 * <p>An entry with no {@code proximo_reporte} date is skipped: n8n reports it for a ticker it
 * tracks but has no scheduled report for, and there is nothing to compute "days remaining" from.
 * {@code daysRemaining} is set to zero here as a structurally-required placeholder — {@code
 * GetEarningsUseCase} immediately overwrites it from its own {@code TimeProvider}, which is the only
 * value this mapper's caller ever uses.
 */
final class EarningsMapper {

    private static final Logger log = LoggerFactory.getLogger(EarningsMapper.class);

    private EarningsMapper() {
    }

    static List<EarningsEvent> fromEarnings(Optional<EarningsResponseDto> response) {
        return response
                .map(dto -> {
                    List<EarningsResponseDto.Entry> entries = dto.tickers();
                    if (entries == null) {
                        return List.<EarningsEvent>of();
                    }
                    return entries.stream()
                            .filter(entry -> entry.proximoReporte() != null)
                            .map(entry -> toTicker(entry.ticker()).map(ticker -> toEvent(ticker, entry)))
                            .flatMap(Optional::stream)
                            .toList();
                })
                .orElseGet(List::of);
    }

    private static EarningsEvent toEvent(Ticker ticker, EarningsResponseDto.Entry entry) {
        return EarningsEvent.builder()
                .ticker(ticker)
                .reportDate(entry.proximoReporte())
                .daysRemaining(0)
                .estimatedEps(entry.epsEstimado())
                .historicalSurprise(entry.sorpresaPromedioPct())
                .build();
    }

    private static Optional<Ticker> toTicker(String symbol) {
        try {
            return Optional.of(Ticker.of(symbol));
        } catch (InvalidTickerException e) {
            log.warn("Skipping earnings event for unparseable ticker symbol '{}'", symbol);
            return Optional.empty();
        }
    }
}
