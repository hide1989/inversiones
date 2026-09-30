package co.com.david.inversiones.consumer.n8n.dto;

/** Request body for the two on-demand POST endpoints ({@code /analizar-empresa}, {@code /etf-ficha}). */
public record TickerRequestDto(String ticker) {
}
