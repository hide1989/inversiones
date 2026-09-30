package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.PortfolioResponse;
import co.com.david.inversiones.api.dto.TotalReturnResponse;
import co.com.david.inversiones.usecase.portfolio.CalculateTotalReturnUseCase;
import co.com.david.inversiones.usecase.portfolio.GetPortfolioUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only views over the current portfolio: positions and P&L, and the total return. */
@RestController
@RequestMapping(value = "/api/v1/portfolio", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Portfolio")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final GetPortfolioUseCase getPortfolio;
    private final CalculateTotalReturnUseCase calculateTotalReturn;

    public PortfolioController(GetPortfolioUseCase getPortfolio, CalculateTotalReturnUseCase calculateTotalReturn) {
        this.getPortfolio = getPortfolio;
        this.calculateTotalReturn = calculateTotalReturn;
    }

    @Operation(summary = "Current positions, market value and P&L in the base currency")
    @GetMapping
    public PortfolioResponse portfolio() {
        return PortfolioResponse.from(getPortfolio.execute());
    }

    @Operation(summary = "Total return in COP, asset gain and the exchange-rate effect kept separate")
    @GetMapping("/returns")
    public TotalReturnResponse returns() {
        return TotalReturnResponse.from(calculateTotalReturn.execute());
    }
}
