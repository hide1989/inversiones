package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response shape of {@code /trm-hoy}, captured live on 2026-09-30.
 *
 * <p>Only the two fields the domain needs are declared; every other field the workflow returns
 * ({@code fuente}, {@code hora_utc}, {@code trm_formatted}, ...) is ignored rather than rejected —
 * see the class-level {@link JsonIgnoreProperties}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrmResponseDto(LocalDate fecha, BigDecimal trm) {
}
