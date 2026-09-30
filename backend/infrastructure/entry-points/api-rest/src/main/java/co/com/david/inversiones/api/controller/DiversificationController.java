package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.DiversificationReportDto;
import co.com.david.inversiones.usecase.portfolio.EvaluateDiversificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Checks the portfolio's allocation against the investor profile's ceilings. */
@RestController
@RequestMapping(value = "/api/v1/diversification", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Diversification")
@SecurityRequirement(name = "bearerAuth")
public class DiversificationController {

    private final EvaluateDiversificationUseCase evaluateDiversification;

    public DiversificationController(EvaluateDiversificationUseCase evaluateDiversification) {
        this.evaluateDiversification = evaluateDiversification;
    }

    @Operation(summary = "Allocation weights by ticker and sector, and any ceiling breaches")
    @GetMapping
    public DiversificationReportDto diversification() {
        return DiversificationReportDto.from(evaluateDiversification.execute());
    }
}
