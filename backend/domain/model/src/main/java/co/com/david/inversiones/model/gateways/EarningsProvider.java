package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Ticker;

import java.util.List;
import java.util.Set;

/** Source of the earnings calendar for a set of tickers, backed by n8n. */
public interface EarningsProvider {

    /**
     * Fetches the next known earnings event for each requested ticker that has one.
     *
     * <p>A requested ticker with no upcoming report on the calendar (an ETF, or a stock the
     * upstream workflow has no date for) is simply absent from the result.
     */
    List<EarningsEvent> fetchEvents(Set<Ticker> tickers);
}
