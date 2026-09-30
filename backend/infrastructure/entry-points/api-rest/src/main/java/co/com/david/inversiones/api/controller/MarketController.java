package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.AiSynthesisResponse;
import co.com.david.inversiones.api.dto.EarningsEventDto;
import co.com.david.inversiones.api.dto.FundamentalsDto;
import co.com.david.inversiones.api.dto.WatchlistItemDto;
import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.usecase.market.GetAiSynthesisUseCase;
import co.com.david.inversiones.usecase.market.GetEarningsUseCase;
import co.com.david.inversiones.usecase.market.GetFundamentalsUseCase;
import co.com.david.inversiones.usecase.market.GetWatchlistUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** Externally-sourced market data: AI reads, earnings calendar, watchlist and on-demand fundamentals. */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Market")
@SecurityRequirement(name = "bearerAuth")
public class MarketController {

    private final GetAiSynthesisUseCase getAiSynthesis;
    private final GetEarningsUseCase getEarnings;
    private final GetWatchlistUseCase getWatchlist;
    private final GetFundamentalsUseCase getFundamentals;

    public MarketController(GetAiSynthesisUseCase getAiSynthesis, GetEarningsUseCase getEarnings,
            GetWatchlistUseCase getWatchlist, GetFundamentalsUseCase getFundamentals) {
        this.getAiSynthesis = getAiSynthesis;
        this.getEarnings = getEarnings;
        this.getWatchlist = getWatchlist;
        this.getFundamentals = getFundamentals;
    }

    @Operation(summary = "AI-generated synthesis for every held instrument")
    @GetMapping("/ai-synthesis")
    public AiSynthesisResponse aiSynthesis() {
        return AiSynthesisResponse.from(getAiSynthesis.execute());
    }

    @Operation(summary = "Upcoming earnings for every held instrument, ordered by proximity")
    @GetMapping("/earnings")
    public List<EarningsEventDto> earnings() {
        return getEarnings.execute().stream().map(EarningsEventDto::from).toList();
    }

    @Operation(summary = "n8n's curated watchlist, independent of what is held")
    @GetMapping("/watchlist")
    public List<WatchlistItemDto> watchlist() {
        return getWatchlist.execute().stream().map(WatchlistItemDto::from).toList();
    }

    @Operation(summary = "On-demand fundamentals for one ticker")
    @GetMapping("/fundamentals/{ticker}")
    public ResponseEntity<FundamentalsDto> fundamentals(@PathVariable(name = "ticker") String ticker) {
        Optional<Fundamentals> fundamentals = getFundamentals.execute(Ticker.of(ticker));
        return fundamentals.map(FundamentalsDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
