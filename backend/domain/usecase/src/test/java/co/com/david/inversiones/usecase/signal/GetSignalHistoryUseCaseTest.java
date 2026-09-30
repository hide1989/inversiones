package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSignalHistoryUseCaseTest {

    private static final Ticker QQQ = Ticker.of("QQQ");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 29);

    @Mock
    private SignalSnapshotRepository snapshotRepository;
    @InjectMocks
    private GetSignalHistoryUseCase useCase;

    @Test
    void delegatesToTheSnapshotRepository() {
        SignalSnapshot snapshot = SignalSnapshot.builder()
                .ticker(QQQ).snapshotDate(TO).scores(Map.of()).build();
        when(snapshotRepository.findBetween(QQQ, FROM, TO)).thenReturn(List.of(snapshot));

        assertThat(useCase.execute(QQQ, FROM, TO)).containsExactly(snapshot);
    }
}
