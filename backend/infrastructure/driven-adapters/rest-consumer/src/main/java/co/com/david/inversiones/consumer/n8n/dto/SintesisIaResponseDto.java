package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape of {@code /sintesis-ia} (WF-17): feeds {@code AiSynthesisProvider}.
 *
 * <p>{@code señal} is read via an explicit {@link JsonProperty} rather than relying on the
 * snake-case naming strategy: it is a single already-lowercase word containing a non-ASCII
 * character, which the naming strategy's case-splitting is not guaranteed to leave untouched.
 *
 * <p>{@code sinDatos} is {@code true} when WF-17's LLM call failed after retries for this ticker;
 * every other field is then a clean placeholder ("Sin analisis disponible...", a neutral hold) that
 * must not be presented as a real assessment — see {@code AiSynthesisMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SintesisIaResponseDto(String generado, List<Entry> sintesis) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String ticker,
            BigDecimal scoreIa,
            @JsonProperty("señal") String senal,
            String conviction,
            String horizonte,
            String resumen,
            List<String> catalizadores,
            List<String> riesgos,
            Boolean sinDatos) {
    }
}
