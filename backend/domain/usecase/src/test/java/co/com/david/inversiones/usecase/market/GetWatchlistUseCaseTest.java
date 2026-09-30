package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.WatchlistItem;
import co.com.david.inversiones.model.gateways.WatchlistProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWatchlistUseCaseTest {

    @Mock
    private WatchlistProvider watchlistProvider;
    @InjectMocks
    private GetWatchlistUseCase useCase;

    @Test
    void returnsWhateverTheProviderReports() {
        WatchlistItem item = WatchlistItem.builder()
                .ticker(Ticker.of("NVDA")).price(new BigDecimal("228.86"))
                .technicalScore(new BigDecimal("8")).wsbScore(new BigDecimal("9.5"))
                .combinedScore(new BigDecimal("8.5")).signal("FUERTE COMPRA").build();
        when(watchlistProvider.fetchWatchlist()).thenReturn(List.of(item));

        List<WatchlistItem> result = useCase.execute();

        assertThat(result).containsExactly(item);
    }

    @Test
    void anEmptyUpstreamProducesAnEmptyList() {
        when(watchlistProvider.fetchWatchlist()).thenReturn(List.of());

        List<WatchlistItem> result = useCase.execute();

        assertThat(result).isEmpty();
    }
}
