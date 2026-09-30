package co.com.david.inversiones.consumer.n8n;

import co.com.david.inversiones.consumer.n8n.dto.BvcPreciosResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.IndicadoresTecnicosResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.InsiderTradingResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.NewsSentimentResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.PreciosHoyResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.RatingsAnalistasResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.RedditResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.StocktwitsResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.TrmResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Raw access to every n8n webhook this module consumes, one method per endpoint.
 *
 * <p>Every method is guarded by its own named circuit breaker and retry (configured in
 * {@code application.yml} under {@code resilience4j.*}, one instance per endpoint group), so a
 * single flaky source is retried and, if it stays down, its circuit opens independently of every
 * other endpoint. These methods deliberately let the underlying exception propagate — a
 * {@link org.springframework.web.client.RestClientException}, a Jackson parse failure, or
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException} once a breaker is open —
 * rather than swallowing it here: every caller in this package (the three provider adapters) wraps
 * its own call to this class and turns any failure into "this source contributed nothing," which is
 * what {@code CombinedScore.from} already knows how to do with a source that never showed up. That
 * degrade-on-failure behaviour therefore also holds when this gateway is instantiated directly, with
 * no Spring proxy in front of it (as the unit tests in this module do) — the resilience annotations
 * only add retrying and circuit-breaking on top; they are not what makes a failure non-fatal.
 *
 * <p>Public only so the provider adapters in this package's subpackages can call it; it is not meant
 * to be consumed outside this module. Each caller is a different Spring bean, which matters because
 * the resilience annotations rely on the Spring AOP proxy — that proxy only intercepts calls that
 * cross a bean boundary, never a method calling another method on {@code this}.
 */
@Component
public class N8nGateway {

    private final RestClient restClient;

    public N8nGateway(@Qualifier("n8nRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "trm")
    @Retry(name = "trm")
    public TrmResponseDto fetchTrm() {
        return restClient.get().uri("/trm-hoy").retrieve().body(TrmResponseDto.class);
    }

    @CircuitBreaker(name = "precios-hoy")
    @Retry(name = "precios-hoy")
    public PreciosHoyResponseDto fetchPreciosHoy() {
        return restClient.get().uri("/precios-hoy").retrieve().body(PreciosHoyResponseDto.class);
    }

    @CircuitBreaker(name = "bvc-precios")
    @Retry(name = "bvc-precios")
    public BvcPreciosResponseDto fetchBvcPrecios() {
        return restClient.get().uri("/bvc-precios").retrieve().body(BvcPreciosResponseDto.class);
    }

    @CircuitBreaker(name = "indicadores-tecnicos")
    @Retry(name = "indicadores-tecnicos")
    public IndicadoresTecnicosResponseDto fetchIndicadoresTecnicos() {
        return restClient.get().uri("/indicadores-tecnicos").retrieve().body(IndicadoresTecnicosResponseDto.class);
    }

    @CircuitBreaker(name = "news-sentiment")
    @Retry(name = "news-sentiment")
    public NewsSentimentResponseDto fetchNewsSentiment() {
        return restClient.get().uri("/news-sentiment").retrieve().body(NewsSentimentResponseDto.class);
    }

    @CircuitBreaker(name = "ratings-analistas")
    @Retry(name = "ratings-analistas")
    public RatingsAnalistasResponseDto fetchRatingsAnalistas() {
        return restClient.get().uri("/ratings-analistas").retrieve().body(RatingsAnalistasResponseDto.class);
    }

    @CircuitBreaker(name = "insider-trading")
    @Retry(name = "insider-trading")
    public InsiderTradingResponseDto fetchInsiderTrading() {
        return restClient.get().uri("/insider-trading").retrieve().body(InsiderTradingResponseDto.class);
    }

    @CircuitBreaker(name = "stocktwits")
    @Retry(name = "stocktwits")
    public StocktwitsResponseDto fetchStocktwits() {
        return restClient.get().uri("/stocktwits").retrieve().body(StocktwitsResponseDto.class);
    }

    @CircuitBreaker(name = "reddit")
    @Retry(name = "reddit")
    public RedditResponseDto fetchReddit() {
        return restClient.get().uri("/reddit").retrieve().body(RedditResponseDto.class);
    }
}
