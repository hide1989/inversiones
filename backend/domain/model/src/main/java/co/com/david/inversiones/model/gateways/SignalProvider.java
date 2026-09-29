package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Source of fresh trading signal snapshots, backed by n8n. */
public interface SignalProvider {

    /**
     * Fetches a snapshot for each requested ticker it could gather any data for.
     *
     * <p>Returns one snapshot per ticker it could gather any data for and omits the rest: a ticker
     * with no upstream data at all is simply absent from the result rather than represented by a
     * placeholder.
     */
    List<SignalSnapshot> fetchSnapshots(Set<Ticker> tickers, LocalDate date);
}
