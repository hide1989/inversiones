package co.com.david.inversiones.model;

import java.util.Objects;

/** A tradable instrument the portfolio can hold. */
public final class Instrument {

    private final Long id;
    private final Ticker ticker;
    private final String name;
    private final InstrumentType type;
    private final String sector;
    private final String exchange;
    private final Currency currency;

    private Instrument(Builder builder) {
        this.id = builder.id;
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.sector = Objects.requireNonNull(builder.sector, "sector must not be null");
        this.exchange = Objects.requireNonNull(builder.exchange, "exchange must not be null");
        this.currency = Objects.requireNonNull(builder.currency, "currency must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long id() {
        return id;
    }

    public Ticker ticker() {
        return ticker;
    }

    public String name() {
        return name;
    }

    public InstrumentType type() {
        return type;
    }

    public String sector() {
        return sector;
    }

    public String exchange() {
        return exchange;
    }

    public Currency currency() {
        return currency;
    }

    /** Fluent builder for {@link Instrument}. */
    public static final class Builder {

        private Long id;
        private Ticker ticker;
        private String name;
        private InstrumentType type;
        private String sector;
        private String exchange;
        private Currency currency;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(InstrumentType type) {
            this.type = type;
            return this;
        }

        public Builder sector(String sector) {
            this.sector = sector;
            return this;
        }

        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public Instrument build() {
            return new Instrument(this);
        }
    }
}
