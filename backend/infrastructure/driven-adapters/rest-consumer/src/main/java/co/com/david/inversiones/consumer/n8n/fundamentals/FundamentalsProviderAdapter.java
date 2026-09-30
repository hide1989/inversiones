package co.com.david.inversiones.consumer.n8n.fundamentals;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.consumer.n8n.dto.AnalizarEmpresaResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.EtfFichaResponseDto;
import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.FundamentalsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link FundamentalsProvider} backed by n8n's {@code /analizar-empresa} (WF-06) and, for ETFs,
 * {@code /etf-ficha} (WF-08).
 *
 * <p>Always calls {@code /analizar-empresa} first — it is the only endpoint that reports {@code
 * es_etf}, so there is no way to know which route a ticker needs without asking it. When that
 * response says {@code es_etf: true}, a second call to {@code /etf-ficha} follows, since that
 * workflow (not {@code /analizar-empresa}) carries an ETF's actual multiples; a failure of that
 * second call still returns a recognized-but-dataless ETF result rather than failing the whole
 * request. On-demand, not cached: this mirrors the dashboard's per-click "analizar fundamentales"
 * action rather than a scheduled feed.
 */
@Component
public class FundamentalsProviderAdapter implements FundamentalsProvider {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsProviderAdapter.class);

    private final N8nGateway gateway;

    public FundamentalsProviderAdapter(N8nGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Optional<Fundamentals> fetchFundamentals(Ticker ticker) {
        Optional<AnalizarEmpresaResponseDto> analysis = N8nSafeCall.safely(
                log, "/analizar-empresa", () -> gateway.postAnalizarEmpresa(ticker.symbol()));

        if (analysis.isEmpty()) {
            return Optional.empty();
        }

        AnalizarEmpresaResponseDto dto = analysis.get();
        if (Boolean.TRUE.equals(dto.esEtf())) {
            Optional<EtfFichaResponseDto> etfFicha = N8nSafeCall.safely(
                    log, "/etf-ficha", () -> gateway.postEtfFicha(ticker.symbol()));
            return Optional.of(FundamentalsMapper.fromEtf(ticker, etfFicha));
        }

        return Optional.of(FundamentalsMapper.fromEquity(ticker, dto));
    }
}
