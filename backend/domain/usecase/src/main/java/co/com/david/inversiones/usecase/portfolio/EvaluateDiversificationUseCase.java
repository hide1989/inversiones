package co.com.david.inversiones.usecase.portfolio;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.InstrumentRepository;

import java.util.*;

/**
 * Checks the portfolio against the allocation ceilings of the investor profile.
 *
 * <p>Weights are computed on market value, which is the exposure actually carried today rather than
 * the one that was bought. Lots of the same ticker held at different brokers are added together:
 * they are one exposure, and checking them separately would let two 15% lots hide a 30%
 * concentration. An instrument absent from the catalog is treated as an equity in an unknown sector
 * so that it falls under the stricter ceiling instead of escaping the check.
 */
public class EvaluateDiversificationUseCase {

    private static final String UNKNOWN_SECTOR = "UNKNOWN";

    private final GetPortfolioUseCase getPortfolio;
    private final InstrumentRepository instrumentRepository;

    public EvaluateDiversificationUseCase(GetPortfolioUseCase getPortfolio,
                                          InstrumentRepository instrumentRepository) {
        this.getPortfolio = getPortfolio;
        this.instrumentRepository = instrumentRepository;
    }

    public DiversificationReport execute() {
        PortfolioValuation valuation = getPortfolio.execute();
        Money total = valuation.totalMarketValue();

        Map<String, Money> valueByTicker = new LinkedHashMap<>();
        Map<String, Money> valueBySector = new LinkedHashMap<>();
        Map<String, InstrumentType> typeByTicker = new LinkedHashMap<>();

        for (ValuedPosition position : valuation.positions()) {
            Ticker ticker = position.position().ticker();
            Optional<Instrument> instrument = instrumentRepository.findByTicker(ticker);
            InstrumentType type = instrument.map(Instrument::type).orElse(InstrumentType.EQUITY);
            String sector = instrument.map(Instrument::sector).orElse(UNKNOWN_SECTOR);

            typeByTicker.put(ticker.symbol(), type);
            valueByTicker.merge(ticker.symbol(), position.marketValue(), Money::add);
            valueBySector.merge(sector, position.marketValue(), Money::add);
        }

        Map<String, Percentage> weightByTicker = weights(valueByTicker, total);
        Map<String, Percentage> weightBySector = weights(valueBySector, total);

        List<DiversificationViolation> violations = new ArrayList<>();
        weightByTicker.forEach((symbol, weight) -> {
            DiversificationLimit limit = typeByTicker.get(symbol).isFund()
                    ? DiversificationLimit.SINGLE_ETF
                    : DiversificationLimit.SINGLE_EQUITY;
            addIfBreached(violations, limit, symbol, weight);
        });
        weightBySector.forEach((sector, weight) ->
                addIfBreached(violations, DiversificationLimit.SECTOR, sector, weight));

        return DiversificationReport.builder()
                .violations(violations)
                .totalMarketValue(total)
                .weightByTicker(weightByTicker)
                .weightBySector(weightBySector)
                .build();
    }

    private Map<String, Percentage> weights(Map<String, Money> values, Money total) {
        Map<String, Percentage> weights = new LinkedHashMap<>();
        values.forEach((key, value) -> weights.put(key, Percentage.between(value, total)));
        return weights;
    }

    private void addIfBreached(List<DiversificationViolation> violations, DiversificationLimit limit,
                               String subject, Percentage actual) {
        if (actual.value().compareTo(limit.allowedWeight().value()) > 0) {
            violations.add(new DiversificationViolation(limit, subject, actual, limit.allowedWeight()));
        }
    }
}
