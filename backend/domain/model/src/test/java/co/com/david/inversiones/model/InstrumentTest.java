package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentTest {

    @Test
    void identifiesFundsSoThatAllocationLimitsCanDiffer() {
        Instrument etf = Instrument.builder()
                .ticker(Ticker.of("QQQ"))
                .name("Invesco QQQ Trust")
                .type(InstrumentType.ETF)
                .sector("Broad Market")
                .exchange("NASDAQ")
                .currency(Currency.USD)
                .build();
        Instrument equity = Instrument.builder()
                .ticker(Ticker.of("TSLA"))
                .name("Tesla Inc.")
                .type(InstrumentType.EQUITY)
                .sector("Consumer Discretionary")
                .exchange("NASDAQ")
                .currency(Currency.USD)
                .build();

        assertThat(etf.type().isFund()).isTrue();
        assertThat(equity.type().isFund()).isFalse();
    }
}
