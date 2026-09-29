package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final LocalDate TRADED_AT = LocalDate.of(2026, 6, 30);
    private static final String HAPI_BROKER = "Hapi";

    private static Transaction.Builder buy() {
        return Transaction.builder()
                .ticker(Ticker.of("QQQ"))
                .brokerName("Hapi")
                .type(TransactionType.BUY)
                .quantity(Quantity.of("0.486372"))
                .unitPrice(Money.usd("616.81"))
                .fees(Money.usd("1.50"))
                .tradedAt(TRADED_AT);
    }

    @Test
    void grossAmountIsQuantityTimesUnitPrice() {
        assertThat(buy().build().grossAmount().amount()).isEqualByComparingTo("299.9991133200");
    }

    @Test
    void aPurchaseCostsTheGrossAmountPlusFees() {
        assertThat(buy().build().netAmount().amount()).isEqualByComparingTo("301.4991133200");
    }

    @Test
    void aSaleYieldsTheGrossAmountMinusFees() {
        Transaction sale = buy().type(TransactionType.SELL).build();
        assertThat(sale.netAmount().amount()).isEqualByComparingTo("298.4991133200");
    }

    @Test
    void feesDefaultToZeroInTheTransactionCurrency() {
        Transaction withoutFees = Transaction.builder()
                .ticker(Ticker.of("VOO"))
                .brokerName("Hapi")
                .type(TransactionType.BUY)
                .quantity(Quantity.of("1"))
                .unitPrice(Money.usd("598.11"))
                .tradedAt(TRADED_AT)
                .build();
        assertThat(withoutFees.fees()).isEqualTo(Money.zero(Currency.USD));
        assertThat(withoutFees.netAmount()).isEqualTo(Money.usd("598.11"));
    }

    @Test
    void rejectsFeesInADifferentCurrencyThanTheUnitPrice() {
        assertThatThrownBy(() -> buy().fees(Money.cop("5000")).build())
                .isInstanceOf(co.com.david.inversiones.model.exception.CurrencyMismatchException.class);
    }

    @Test
    void rejectsABuilderMissingEveryFieldButTheTicker() {
        assertThatThrownBy(() -> Transaction.builder().ticker(Ticker.of("QQQ")).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void namesTheMissingFieldWhenExactlyOneIsAbsent() {
        // Order-independent: every other field is supplied, so only `type` can be reported.
        assertThatThrownBy(() -> Transaction.builder()
                .ticker(Ticker.of("QQQ"))
                .brokerName(HAPI_BROKER)
                .quantity(Quantity.of("1"))
                .unitPrice(Money.usd("600.00"))
                .tradedAt(TRADED_AT)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }
}
