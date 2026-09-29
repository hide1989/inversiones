package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.SignalProvider;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshSignalSnapshotUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final Ticker QQQ = Ticker.of("QQQ");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SignalProvider signalProvider;
    @Mock
    private SignalSnapshotRepository snapshotRepository;
    @Mock
    private TimeProvider timeProvider;
    @InjectMocks
    private RefreshSignalSnapshotUseCase useCase;

    private static Transaction holding(Ticker ticker) {
        return Transaction.builder()
                .ticker(ticker).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(TODAY.minusMonths(1)).build();
    }

    @Test
    void storesTheSnapshotsTheProviderReturned() {
        SignalSnapshot fetched = SignalSnapshot.builder()
                .ticker(QQQ).snapshotDate(TODAY)
                .scores(Map.of(SignalSource.TECHNICAL, new BigDecimal("8"))).build();
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(signalProvider.fetchSnapshots(Set.of(QQQ), TODAY)).thenReturn(List.of(fetched));
        when(snapshotRepository.saveAll(List.of(fetched))).thenReturn(List.of(fetched));

        assertThat(useCase.execute()).isEqualTo(1);
        verify(snapshotRepository).saveAll(List.of(fetched));
    }

    @Test
    void storesNothingAndReportsZeroWhenTheProviderReturnsNothing() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(signalProvider.fetchSnapshots(Set.of(QQQ), TODAY)).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();
        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void doesNotCallTheProviderWhenNothingIsHeld() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();
        verifyNoInteractions(signalProvider, snapshotRepository);
    }
}
