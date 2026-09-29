package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InsufficientPositionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final String HAPI = "Hapi";

    private static Transaction trade(TransactionType type, String quantity, String unitPrice, int dayOfMonth) {
        return Transaction.builder()
                .ticker(QQQ)
                .brokerName(HAPI)
                .type(type)
                .quantity(Quantity.of(quantity))
                .unitPrice(Money.usd(unitPrice))
                .tradedAt(LocalDate.of(2026, 6, dayOfMonth))
                .build();
    }

    @Test
    void aSinglePurchaseSetsAverageCostToTheUnitPrice() {
        Position position = Position.fromTransactions(QQQ, HAPI,
                List.of(trade(TransactionType.BUY, "2", "100.00", 1)));

        assertThat(position.quantity()).isEqualTo(Quantity.of("2"));
        assertThat(position.totalCost()).isEqualTo(Money.usd("200.00"));
        assertThat(position.averageCost().amount()).isEqualByComparingTo("100");
        assertThat(position.realizedPnl()).isEqualTo(Money.zero(Currency.USD));
    }

    @Test
    void twoPurchasesAtDifferentPricesWeightTheAverageCostByQuantity() {
        Position position = Position.fromTransactions(QQQ, HAPI, List.of(
                trade(TransactionType.BUY, "1", "100.00", 1),
                trade(TransactionType.BUY, "3", "200.00", 2)));

        assertThat(position.quantity()).isEqualTo(Quantity.of("4"));
        assertThat(position.totalCost()).isEqualTo(Money.usd("700.00"));
        assertThat(position.averageCost().amount()).isEqualByComparingTo("175");
    }

    @Test
    void aPartialSaleLeavesAverageCostUnchangedAndRealizesTheGain() {
        Position position = Position.fromTransactions(QQQ, HAPI, List.of(
                trade(TransactionType.BUY, "1", "100.00", 1),
                trade(TransactionType.BUY, "3", "200.00", 2),
                trade(TransactionType.SELL, "2", "250.00", 3)));

        assertThat(position.quantity()).isEqualTo(Quantity.of("2"));
        assertThat(position.averageCost().amount()).isEqualByComparingTo("175");
        assertThat(position.totalCost().amount()).isEqualByComparingTo("350");
        // Sold 2 at 250 (500) against a cost basis of 2 x 175 (350).
        assertThat(position.realizedPnl().amount()).isEqualByComparingTo("150");
    }

    @Test
    void feesReduceTheProceedsOfASale() {
        Transaction saleWithFees = Transaction.builder()
                .ticker(QQQ).brokerName(HAPI).type(TransactionType.SELL)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("250.00"))
                .fees(Money.usd("2.00")).tradedAt(LocalDate.of(2026, 6, 3))
                .build();

        Position position = Position.fromTransactions(QQQ, HAPI,
                List.of(trade(TransactionType.BUY, "1", "100.00", 1), saleWithFees));

        assertThat(position.realizedPnl().amount()).isEqualByComparingTo("148");
        assertThat(position.isOpen()).isFalse();
    }

    @Test
    void sellingEverythingClosesThePosition() {
        Position position = Position.fromTransactions(QQQ, HAPI, List.of(
                trade(TransactionType.BUY, "2", "100.00", 1),
                trade(TransactionType.SELL, "2", "150.00", 2)));

        assertThat(position.quantity().isZero()).isTrue();
        assertThat(position.totalCost().isZero()).isTrue();
        assertThat(position.isOpen()).isFalse();
        assertThat(position.realizedPnl().amount()).isEqualByComparingTo("100");
    }

    @Test
    void rejectsSellingMoreSharesThanAreHeld() {
        assertThatThrownBy(() -> Position.fromTransactions(QQQ, HAPI, List.of(
                trade(TransactionType.BUY, "1", "100.00", 1),
                trade(TransactionType.SELL, "2", "150.00", 2))))
                .isInstanceOf(InsufficientPositionException.class)
                .hasMessageContaining("QQQ");
    }

    @Test
    void appliesTransactionsInTradeDateOrderRegardlessOfInputOrder() {
        Position position = Position.fromTransactions(QQQ, HAPI, List.of(
                trade(TransactionType.SELL, "1", "150.00", 2),
                trade(TransactionType.BUY, "2", "100.00", 1)));

        assertThat(position.quantity()).isEqualTo(Quantity.of("1"));
        assertThat(position.realizedPnl().amount()).isEqualByComparingTo("50");
    }

    @Test
    void deriveAllGroupsByTickerAndBrokerAndKeepsOnlyOpenPositions() {
        Ticker voo = Ticker.of("VOO");
        Transaction qqqHapi = trade(TransactionType.BUY, "2", "100.00", 1);
        Transaction qqqArq = Transaction.builder()
                .ticker(QQQ).brokerName("ARQ").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("120.00"))
                .tradedAt(LocalDate.of(2026, 6, 1)).build();
        Transaction vooBuy = Transaction.builder()
                .ticker(voo).brokerName(HAPI).type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("598.11"))
                .tradedAt(LocalDate.of(2026, 6, 1)).build();
        Transaction vooSellAll = Transaction.builder()
                .ticker(voo).brokerName(HAPI).type(TransactionType.SELL)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("610.00"))
                .tradedAt(LocalDate.of(2026, 6, 2)).build();

        List<Position> positions =
                Position.deriveAll(List.of(qqqHapi, qqqArq, vooBuy, vooSellAll));

        assertThat(positions).hasSize(2);
        assertThat(positions).allMatch(Position::isOpen);
        assertThat(positions)
                .extracting(position -> position.ticker().symbol() + "@" + position.brokerName())
                .containsExactlyInAnyOrder("QQQ@Hapi", "QQQ@ARQ");
    }

    @Test
    void deriveAllOnAnEmptyHistoryProducesNoPositions() {
        assertThat(Position.deriveAll(List.of())).isEmpty();
    }

    @Test
    void anEmptyTransactionListProducesAClosedPositionAtZeroCost() {
        Position position = Position.fromTransactions(QQQ, HAPI, List.of());

        assertThat(position.isOpen()).isFalse();
        assertThat(position.quantity().isZero()).isTrue();
        assertThat(position.averageCost()).isEqualTo(Money.zero(Currency.USD));
    }
}
