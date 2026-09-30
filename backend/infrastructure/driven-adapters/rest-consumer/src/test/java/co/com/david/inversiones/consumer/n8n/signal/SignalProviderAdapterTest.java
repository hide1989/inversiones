package co.com.david.inversiones.consumer.n8n.signal;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.SignalSource;
import co.com.david.inversiones.model.Ticker;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests against the six real signal payloads captured in {@code src/test/resources/n8n}.
 *
 * <p>The rule under test: a failing signal endpoint contributes no score for its own source and
 * must not fail the batch. {@code CombinedScore.from} redistributes the weight of an absent source
 * across the rest, so a zero score would be read as a sell signal — "the API was down" must never
 * look like one.
 */
class SignalProviderAdapterTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 9, 30);
    private static final Set<Ticker> ALL_TICKERS = Set.of(
            Ticker.of("QQQ"), Ticker.of("TSLA"), Ticker.of("GOOGL"));

    private MockWebServer mockWebServer;
    private final Map<String, Supplier<MockResponse>> responses = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        responses.put("/indicadores-tecnicos", okFixture("indicadores-tecnicos"));
        responses.put("/news-sentiment", okFixture("news-sentiment"));
        responses.put("/ratings-analistas", okFixture("ratings-analistas"));
        responses.put("/insider-trading", okFixture("insider-trading"));
        responses.put("/stocktwits", okFixture("stocktwits"));
        responses.put("/reddit", okFixture("reddit"));
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Supplier<MockResponse> supplier = responses.get(request.getPath());
                if (supplier == null) {
                    throw new IllegalStateException("Unexpected path: " + request.getPath());
                }
                return supplier.get();
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private SignalProviderAdapter adapterWithReadTimeout(Duration readTimeout) {
        RestClient restClient = TestRestClients.forMockServer(mockWebServer, readTimeout);
        return new SignalProviderAdapter(new N8nGateway(restClient));
    }

    private static Supplier<MockResponse> okFixture(String name) {
        return () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load(name));
    }

    @Test
    void aServerErrorFromOneEndpointLeavesThatSourceAbsentFromEverySnapshotWhileTheOthersRemain() {
        responses.put("/stocktwits", () -> new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        List<SignalSnapshot> snapshots = adapterWithReadTimeout(Duration.ofSeconds(5))
                .fetchSnapshots(ALL_TICKERS, SNAPSHOT_DATE);

        assertThat(snapshots).isNotEmpty();
        for (SignalSnapshot snapshot : snapshots) {
            assertThat(snapshot.availableSources()).doesNotContain(SignalSource.SOCIAL);
            assertThat(snapshot.scoreOf(SignalSource.SOCIAL)).isEmpty();
        }
        // At least one of the other five sources is present on every ticker in the fixtures.
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.availableSources()).isNotEmpty());
        // No score of zero silently stands in for the failed source.
        assertThat(snapshots).noneMatch(s -> s.scoreOf(SignalSource.SOCIAL).map(v -> v.signum() == 0).orElse(false));
    }

    @Test
    void aReadTimeoutFromOneEndpointIsHandledIdenticallyToAServerError() {
        responses.put("/stocktwits", () -> new MockResponse()
                .setHeadersDelay(2, TimeUnit.SECONDS)
                .setResponseCode(HttpStatus.OK.value())
                .setBody(Fixtures.load("stocktwits")));

        List<SignalSnapshot> snapshots = adapterWithReadTimeout(Duration.ofMillis(200))
                .fetchSnapshots(ALL_TICKERS, SNAPSHOT_DATE);

        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots).allSatisfy(
                snapshot -> assertThat(snapshot.availableSources()).doesNotContain(SignalSource.SOCIAL));
    }

    @Test
    void malformedJsonFromOneEndpointDoesNotPropagateAJacksonExceptionToTheCaller() {
        responses.put("/stocktwits", () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{ not valid json"));

        List<SignalSnapshot> snapshots = adapterWithReadTimeout(Duration.ofSeconds(5))
                .fetchSnapshots(ALL_TICKERS, SNAPSHOT_DATE);

        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots).allSatisfy(
                snapshot -> assertThat(snapshot.availableSources()).doesNotContain(SignalSource.SOCIAL));
    }

    @Test
    void anUnknownExtraFieldInThePayloadStillDeserializesSuccessfully() {
        String withExtraField = Fixtures.load("stocktwits")
                .replaceFirst("\\{", "{\"unexpected_new_field\": {\"nested\": true},");
        responses.put("/stocktwits", () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(withExtraField));

        List<SignalSnapshot> snapshots = adapterWithReadTimeout(Duration.ofSeconds(5))
                .fetchSnapshots(ALL_TICKERS, SNAPSHOT_DATE);

        Ticker qqq = Ticker.of("QQQ");
        SignalSnapshot qqqSnapshot = snapshots.stream()
                .filter(s -> s.ticker().equals(qqq))
                .findFirst()
                .orElseThrow();
        assertThat(qqqSnapshot.availableSources()).contains(SignalSource.SOCIAL);
        assertThat(qqqSnapshot.scoreOf(SignalSource.SOCIAL)).isPresent();
    }

    @Test
    void assemblesASnapshotPerTickerFromAllSixSourcesWhenEverythingSucceeds() {
        List<SignalSnapshot> snapshots = adapterWithReadTimeout(Duration.ofSeconds(5))
                .fetchSnapshots(Set.of(Ticker.of("TSLA")), SNAPSHOT_DATE);

        SignalSnapshot tsla = snapshots.getFirst();
        assertThat(tsla.snapshotDate()).isEqualTo(SNAPSHOT_DATE);
        assertThat(tsla.availableSources()).containsExactlyInAnyOrder(
                SignalSource.TECHNICAL, SignalSource.NEWS, SignalSource.ANALYST,
                SignalSource.INSIDER, SignalSource.SOCIAL, SignalSource.WSB);
    }
}
