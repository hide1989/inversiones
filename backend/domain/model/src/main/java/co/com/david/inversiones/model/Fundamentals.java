package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The valuation and health multiples n8n reports for one ticker.
 *
 * <p>Backed by two different upstream shapes depending on {@code isEtf}: an equity goes through
 * {@code /analizar-empresa} (WF-06), which carries the full multiple set below; an ETF goes through
 * {@code /etf-ficha} (WF-08) instead, which only ever fills {@code per}, {@code pb}, {@code beta}
 * and {@code dividendYield} — the rest stay {@code null} rather than being invented. Every field
 * beyond {@code ticker}/{@code isEtf}/{@code hasData} may legitimately be {@code null}: the payload
 * itself reports many multiples as {@code null} even for a covered ticker (a missing PEG when
 * earnings growth is undefined, for example), so {@code null} here means "upstream had nothing,"
 * not "the field was not modeled."
 */
public final class Fundamentals {

    private final Ticker ticker;
    private final boolean isEtf;
    private final boolean hasData;
    private final BigDecimal per;
    private final BigDecimal peg;
    private final BigDecimal priceToBook;
    private final BigDecimal priceToSales;
    private final BigDecimal evToEbitda;
    private final BigDecimal roe;
    private final BigDecimal netMargin;
    private final BigDecimal revenueCagr5y;
    private final BigDecimal epsCagr5y;
    private final BigDecimal debtToEquity;
    private final BigDecimal beta;
    private final BigDecimal dividendYield;
    private final BigDecimal currentRatio;

    private Fundamentals(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.isEtf = builder.isEtf;
        this.hasData = builder.hasData;
        this.per = builder.per;
        this.peg = builder.peg;
        this.priceToBook = builder.priceToBook;
        this.priceToSales = builder.priceToSales;
        this.evToEbitda = builder.evToEbitda;
        this.roe = builder.roe;
        this.netMargin = builder.netMargin;
        this.revenueCagr5y = builder.revenueCagr5y;
        this.epsCagr5y = builder.epsCagr5y;
        this.debtToEquity = builder.debtToEquity;
        this.beta = builder.beta;
        this.dividendYield = builder.dividendYield;
        this.currentRatio = builder.currentRatio;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Ticker ticker() {
        return ticker;
    }

    public boolean isEtf() {
        return isEtf;
    }

    public boolean hasData() {
        return hasData;
    }

    public BigDecimal per() {
        return per;
    }

    public BigDecimal peg() {
        return peg;
    }

    public BigDecimal priceToBook() {
        return priceToBook;
    }

    public BigDecimal priceToSales() {
        return priceToSales;
    }

    public BigDecimal evToEbitda() {
        return evToEbitda;
    }

    public BigDecimal roe() {
        return roe;
    }

    public BigDecimal netMargin() {
        return netMargin;
    }

    public BigDecimal revenueCagr5y() {
        return revenueCagr5y;
    }

    public BigDecimal epsCagr5y() {
        return epsCagr5y;
    }

    public BigDecimal debtToEquity() {
        return debtToEquity;
    }

    public BigDecimal beta() {
        return beta;
    }

    public BigDecimal dividendYield() {
        return dividendYield;
    }

    public BigDecimal currentRatio() {
        return currentRatio;
    }

    /** Fluent builder for {@link Fundamentals}, following the convention of {@code SignalSnapshot.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private boolean isEtf;
        private boolean hasData;
        private BigDecimal per;
        private BigDecimal peg;
        private BigDecimal priceToBook;
        private BigDecimal priceToSales;
        private BigDecimal evToEbitda;
        private BigDecimal roe;
        private BigDecimal netMargin;
        private BigDecimal revenueCagr5y;
        private BigDecimal epsCagr5y;
        private BigDecimal debtToEquity;
        private BigDecimal beta;
        private BigDecimal dividendYield;
        private BigDecimal currentRatio;

        private Builder() {
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder isEtf(boolean isEtf) {
            this.isEtf = isEtf;
            return this;
        }

        public Builder hasData(boolean hasData) {
            this.hasData = hasData;
            return this;
        }

        public Builder per(BigDecimal per) {
            this.per = per;
            return this;
        }

        public Builder peg(BigDecimal peg) {
            this.peg = peg;
            return this;
        }

        public Builder priceToBook(BigDecimal priceToBook) {
            this.priceToBook = priceToBook;
            return this;
        }

        public Builder priceToSales(BigDecimal priceToSales) {
            this.priceToSales = priceToSales;
            return this;
        }

        public Builder evToEbitda(BigDecimal evToEbitda) {
            this.evToEbitda = evToEbitda;
            return this;
        }

        public Builder roe(BigDecimal roe) {
            this.roe = roe;
            return this;
        }

        public Builder netMargin(BigDecimal netMargin) {
            this.netMargin = netMargin;
            return this;
        }

        public Builder revenueCagr5y(BigDecimal revenueCagr5y) {
            this.revenueCagr5y = revenueCagr5y;
            return this;
        }

        public Builder epsCagr5y(BigDecimal epsCagr5y) {
            this.epsCagr5y = epsCagr5y;
            return this;
        }

        public Builder debtToEquity(BigDecimal debtToEquity) {
            this.debtToEquity = debtToEquity;
            return this;
        }

        public Builder beta(BigDecimal beta) {
            this.beta = beta;
            return this;
        }

        public Builder dividendYield(BigDecimal dividendYield) {
            this.dividendYield = dividendYield;
            return this;
        }

        public Builder currentRatio(BigDecimal currentRatio) {
            this.currentRatio = currentRatio;
            return this;
        }

        public Fundamentals build() {
            return new Fundamentals(this);
        }
    }
}
