package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.QuoteProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPortfolioUseCaseTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final Ticker TSLA = Ticker.of("TSLA");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private QuoteProvider quoteProvider;
    @InjectMocks
    private GetPortfolioUseCase useCase;

    private static Transaction buy(Ticker ticker, String broker, String quantity, String price) {
        return Transaction.builder()
                .ticker(ticker).brokerName(broker).type(TransactionType.BUY)
                .quantity(Quantity.of(quantity)).unitPrice(Money.usd(price))
                .tradedAt(LocalDate.of(2026, 6, 1)).build();
    }

    private static Quote quote(Ticker ticker, String price, String dayChange) {
        return new Quote(ticker, Money.usd(price), Percentage.of(dayChange));
    }

    @Test
    void valuesAPositionAgainstItsQuote() {
        when(transactionRepository.findAll()).thenReturn(List.of(buy(QQQ, "Hapi", "2", "100.00")));
        when(quoteProvider.findQuotes(Set.of(QQQ))).thenReturn(Map.of(QQQ, quote(QQQ, "150.00", "0.82")));

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.positions()).hasSize(1);
        ValuedPosition valued = valuation.positions().getFirst();
        assertThat(valued.marketValue().amount()).isEqualByComparingTo("300");
        assertThat(valued.unrealizedPnl().amount()).isEqualByComparingTo("100");
        assertThat(valued.unrealizedPnlPercent().value()).isEqualByComparingTo("50.0000");
        assertThat(valued.dayChangePercent().value()).isEqualByComparingTo("0.82");
        assertThat(valued.priced()).isTrue();
    }

    @Test
    void keepsTheSameTickerAtTwoBrokersAsSeparatePositions() {
        when(transactionRepository.findAll()).thenReturn(List.of(
                buy(QQQ, "Hapi", "2", "100.00"),
                buy(QQQ, "ARQ", "1", "120.00")));
        when(quoteProvider.findQuotes(Set.of(QQQ))).thenReturn(Map.of(QQQ, quote(QQQ, "150.00", "0.82")));

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.positions()).hasSize(2);
        assertThat(valuation.positions()).extracting(v -> v.position().brokerName())
                .containsExactlyInAnyOrder("Hapi", "ARQ");
        assertThat(valuation.totalInvested().amount()).isEqualByComparingTo("320");
        assertThat(valuation.totalMarketValue().amount()).isEqualByComparingTo("450");
        assertThat(valuation.totalUnrealizedPnl().amount()).isEqualByComparingTo("130");
    }

    @Test
    void carriesAnUnpricedPositionAtCostAndReportsItAsUnpriced() {
        when(transactionRepository.findAll()).thenReturn(List.of(
                buy(QQQ, "Hapi", "2", "100.00"),
                buy(TSLA, "Hapi", "1", "400.00")));
        when(quoteProvider.findQuotes(anySet())).thenReturn(Map.of(QQQ, quote(QQQ, "150.00", "0.82")));

        PortfolioValuation valuation = useCase.execute();

        ValuedPosition unpriced = valuation.positions().stream()
                .filter(v -> v.position().ticker().equals(TSLA)).findFirst().orElseThrow();
        assertThat(unpriced.priced()).isFalse();
        assertThat(unpriced.marketValue().amount()).isEqualByComparingTo("400");
        assertThat(unpriced.unrealizedPnl().isZero()).isTrue();
        assertThat(valuation.unpricedTickers()).containsExactly(TSLA);
        assertThat(valuation.totalMarketValue().amount()).isEqualByComparingTo("700");
    }

    @Test
    void excludesFullySoldPositions() {
        Transaction purchase = buy(QQQ, "Hapi", "2", "100.00");
        Transaction sale = Transaction.builder()
                .ticker(QQQ).brokerName("Hapi").type(TransactionType.SELL)
                .quantity(Quantity.of("2")).unitPrice(Money.usd("150.00"))
                .tradedAt(LocalDate.of(2026, 6, 2)).build();
        when(transactionRepository.findAll()).thenReturn(List.of(purchase, sale));
        when(quoteProvider.findQuotes(Set.of())).thenReturn(Map.of());

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.positions()).isEmpty();
        assertThat(valuation.totalInvested().isZero()).isTrue();
    }

    @Test
    void anEmptyHistoryProducesAnEmptyValuation() {
        when(transactionRepository.findAll()).thenReturn(List.of());
        when(quoteProvider.findQuotes(Set.of())).thenReturn(Map.of());

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.positions()).isEmpty();
        assertThat(valuation.totalMarketValue()).isEqualTo(Money.zero(Currency.USD));
        assertThat(valuation.unpricedTickers()).isEmpty();
    }
}
