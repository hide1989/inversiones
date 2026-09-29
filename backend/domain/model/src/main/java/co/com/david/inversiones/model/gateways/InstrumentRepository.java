package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.Instrument;
import co.com.david.inversiones.model.Ticker;

import java.util.List;
import java.util.Optional;

/** Persistence port for the instrument catalog. */
public interface InstrumentRepository {

    /** Returns the instrument with this ticker, if it is known. */
    Optional<Instrument> findByTicker(Ticker ticker);

    /** Returns every instrument in the catalog. */
    List<Instrument> findAll();
}
