package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.Fundamentals;

/**
 * Wire representation of a {@link Fundamentals}. Most fields may legitimately be {@code null}: an
 * ETF only ever fills {@code per}, {@code priceToBook}, {@code beta} and {@code dividendYield}.
 */
public record FundamentalsDto(
        String ticker,
        boolean isEtf,
        boolean hasData,
        String per,
        String peg,
        String priceToBook,
        String priceToSales,
        String evToEbitda,
        String roe,
        String netMargin,
        String revenueCagr5y,
        String epsCagr5y,
        String debtToEquity,
        String beta,
        String dividendYield,
        String currentRatio) {

    public static FundamentalsDto from(Fundamentals fundamentals) {
        return new FundamentalsDto(
                fundamentals.ticker().symbol(),
                fundamentals.isEtf(),
                fundamentals.hasData(),
                DecimalStrings.of(fundamentals.per()),
                DecimalStrings.of(fundamentals.peg()),
                DecimalStrings.of(fundamentals.priceToBook()),
                DecimalStrings.of(fundamentals.priceToSales()),
                DecimalStrings.of(fundamentals.evToEbitda()),
                DecimalStrings.of(fundamentals.roe()),
                DecimalStrings.of(fundamentals.netMargin()),
                DecimalStrings.of(fundamentals.revenueCagr5y()),
                DecimalStrings.of(fundamentals.epsCagr5y()),
                DecimalStrings.of(fundamentals.debtToEquity()),
                DecimalStrings.of(fundamentals.beta()),
                DecimalStrings.of(fundamentals.dividendYield()),
                DecimalStrings.of(fundamentals.currentRatio()));
    }
}
