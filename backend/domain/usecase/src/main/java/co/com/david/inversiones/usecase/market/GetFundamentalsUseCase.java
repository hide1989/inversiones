package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.FundamentalsProvider;

import java.util.Optional;

/**
 * Fetches on-demand fundamentals for one ticker, mirroring the dashboard's "analizar
 * fundamentales" action rather than a scheduled batch.
 */
public class GetFundamentalsUseCase {

    private final FundamentalsProvider fundamentalsProvider;

    public GetFundamentalsUseCase(FundamentalsProvider fundamentalsProvider) {
        this.fundamentalsProvider = fundamentalsProvider;
    }

    public Optional<Fundamentals> execute(Ticker ticker) {
        return fundamentalsProvider.fetchFundamentals(ticker);
    }
}
