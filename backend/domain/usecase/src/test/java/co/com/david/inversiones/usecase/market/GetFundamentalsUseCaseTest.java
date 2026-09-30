package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.FundamentalsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFundamentalsUseCaseTest {

    private static final Ticker MSFT = Ticker.of("MSFT");

    @Mock
    private FundamentalsProvider fundamentalsProvider;
    @InjectMocks
    private GetFundamentalsUseCase useCase;

    @Test
    void returnsWhateverTheProviderReports() {
        Fundamentals fundamentals = Fundamentals.builder()
                .ticker(MSFT).isEtf(false).hasData(true).per(new BigDecimal("27.9164")).build();
        when(fundamentalsProvider.fetchFundamentals(MSFT)).thenReturn(Optional.of(fundamentals));

        Optional<Fundamentals> result = useCase.execute(MSFT);

        assertThat(result).contains(fundamentals);
    }

    @Test
    void anUpstreamFailureProducesAnEmptyResultRatherThanAnException() {
        when(fundamentalsProvider.fetchFundamentals(MSFT)).thenReturn(Optional.empty());

        Optional<Fundamentals> result = useCase.execute(MSFT);

        assertThat(result).isEmpty();
    }
}
