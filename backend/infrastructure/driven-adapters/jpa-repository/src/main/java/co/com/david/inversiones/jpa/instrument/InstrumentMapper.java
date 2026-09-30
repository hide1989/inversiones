package co.com.david.inversiones.jpa.instrument;

import co.com.david.inversiones.model.Instrument;
import co.com.david.inversiones.model.Ticker;

/**
 * Hand-written mapping between {@link InstrumentData} and {@link Instrument}.
 *
 * <p>Written explicitly rather than through a reflection-based mapper so that the conversion from
 * the raw {@code ticker} column to the validated, normalized {@link Ticker} value type is a
 * deliberate, visible step rather than something a generic mapper could skip or get subtly wrong.
 */
final class InstrumentMapper {

    private InstrumentMapper() {
    }

    static Instrument toDomain(InstrumentData data) {
        return Instrument.builder()
                .id(data.getId())
                .ticker(Ticker.of(data.getTicker()))
                .name(data.getName())
                .type(data.getType())
                .sector(data.getSector())
                .exchange(data.getExchange())
                .currency(data.getCurrency())
                .build();
    }
}
