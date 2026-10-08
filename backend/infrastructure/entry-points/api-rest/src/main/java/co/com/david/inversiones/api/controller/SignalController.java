package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.SignalRefreshResponse;
import co.com.david.inversiones.api.dto.SignalSnapshotDto;
import co.com.david.inversiones.api.dto.SignalsDashboardResponse;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.SignalRefreshInProgressException;
import co.com.david.inversiones.usecase.signal.GetSignalHistoryUseCase;
import co.com.david.inversiones.usecase.signal.GetSignalsDashboardUseCase;
import co.com.david.inversiones.usecase.signal.RefreshOutcome;
import co.com.david.inversiones.usecase.signal.RefreshSignalSnapshotUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The trading-signals dashboard, and its per-ticker history. */
@RestController
@RequestMapping(value = "/api/v1/signals", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Signals")
@SecurityRequirement(name = "bearerAuth")
public class SignalController {

    private final GetSignalsDashboardUseCase getSignalsDashboard;
    private final GetSignalHistoryUseCase getSignalHistory;
    private final RefreshSignalSnapshotUseCase refreshSignalSnapshot;

    public SignalController(GetSignalsDashboardUseCase getSignalsDashboard, GetSignalHistoryUseCase getSignalHistory,
                            RefreshSignalSnapshotUseCase refreshSignalSnapshot) {
        this.getSignalsDashboard = getSignalsDashboard;
        this.getSignalHistory = getSignalHistory;
        this.refreshSignalSnapshot = refreshSignalSnapshot;
    }

    @Operation(summary = "Signal table with the combined score for every held ticker")
    @GetMapping
    public SignalsDashboardResponse dashboard() {
        return SignalsDashboardResponse.from(getSignalsDashboard.execute());
    }

    @Operation(summary = "Historical snapshots for one ticker between two dates")
    @GetMapping("/history")
    public List<SignalSnapshotDto> history(
            @RequestParam(name = "ticker") String ticker,
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return getSignalHistory.execute(Ticker.of(ticker), from, to).stream().map(SignalSnapshotDto::from).toList();
    }

    @Operation(summary = "Trigger an out-of-schedule signal snapshot refresh", responses = {
            @ApiResponse(responseCode = "200", description = "Refresh ran; returns how many snapshots were stored"),
            @ApiResponse(responseCode = "409", description = "A refresh (scheduled or manual) is already running")
    })
    @PostMapping("/refresh")
    public SignalRefreshResponse refresh() {
        RefreshOutcome outcome = refreshSignalSnapshot.execute();
        return switch (outcome) {
            case RefreshOutcome.Completed completed -> new SignalRefreshResponse(completed.storedCount());
            case RefreshOutcome.AlreadyRunning ignored -> throw new SignalRefreshInProgressException();
        };
    }
}
