package co.com.david.inversiones.consumer.n8n.aisynthesis;

import co.com.david.inversiones.consumer.n8n.dto.SintesisIaResponseDto;
import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Maps {@code /sintesis-ia} entries into {@link AiSynthesis}.
 *
 * <p>Every entry n8n reports is kept, {@code sin_datos} or not: unlike {@code SignalMapper}, which
 * silently drops a source with nothing to say, this mapper must preserve the no-data marker so
 * {@code GetAiSynthesisUseCase} can list those tickers explicitly rather than lose them.
 */
final class AiSynthesisMapper {

    private static final Logger log = LoggerFactory.getLogger(AiSynthesisMapper.class);

    private AiSynthesisMapper() {
    }

    static List<AiSynthesis> fromSintesisIa(Optional<SintesisIaResponseDto> response, LocalDate today) {
        return response
                .map(dto -> {
                    LocalDate snapshotDate = parseDate(dto.generado(), today);
                    List<SintesisIaResponseDto.Entry> entries = dto.sintesis();
                    if (entries == null) {
                        return List.<AiSynthesis>of();
                    }
                    return entries.stream()
                            .map(entry -> toTicker(entry.ticker()).map(ticker -> toSynthesis(ticker, snapshotDate, entry)))
                            .flatMap(Optional::stream)
                            .toList();
                })
                .orElseGet(List::of);
    }

    private static AiSynthesis toSynthesis(Ticker ticker, LocalDate snapshotDate, SintesisIaResponseDto.Entry entry) {
        boolean hasData = !Boolean.TRUE.equals(entry.sinDatos());
        return AiSynthesis.builder()
                .ticker(ticker)
                .snapshotDate(snapshotDate)
                .iaScore(entry.scoreIa())
                .signal(entry.senal())
                .conviction(entry.conviction())
                .horizon(entry.horizonte())
                .summary(entry.resumen())
                .catalysts(entry.catalizadores())
                .risks(entry.riesgos())
                .hasData(hasData)
                .build();
    }

    private static LocalDate parseDate(String generado, LocalDate today) {
        if (generado == null) {
            return today;
        }
        try {
            return OffsetDateTime.parse(generado).toLocalDate();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse sintesis-ia 'generado' timestamp '{}', falling back to today", generado);
            return today;
        }
    }

    private static Optional<Ticker> toTicker(String symbol) {
        try {
            return Optional.of(Ticker.of(symbol));
        } catch (InvalidTickerException e) {
            log.warn("Skipping AI synthesis for unparseable ticker symbol '{}'", symbol);
            return Optional.empty();
        }
    }
}
