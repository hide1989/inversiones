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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

        RefreshOutcome outcome = useCase.execute();

        assertThat(outcome).isEqualTo(new RefreshOutcome.Completed(1));
        verify(snapshotRepository).saveAll(List.of(fetched));
    }

    @Test
    void storesNothingAndReportsZeroWhenTheProviderReturnsNothing() {
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(signalProvider.fetchSnapshots(Set.of(QQQ), TODAY)).thenReturn(List.of());

        assertThat(useCase.execute()).isEqualTo(new RefreshOutcome.Completed(0));
        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void doesNotCallTheProviderWhenNothingIsHeld() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        assertThat(useCase.execute()).isEqualTo(new RefreshOutcome.Completed(0));
        verifyNoInteractions(signalProvider, snapshotRepository);
    }

    /**
     * Proves the guard actually excludes a second, genuinely concurrent call rather than merely
     * asserting a call count (a proxy that would pass even without any guard, since both calls
     * would still happen exactly once each). The first call is blocked inside the stubbed
     * {@code SignalProvider} until the second call has already observed the guard held and
     * returned {@link RefreshOutcome.AlreadyRunning}; only then is the first call released to
     * finish normally.
     */
    @Test
    void refusesASecondCallWhileTheFirstIsStillInFlight() throws Exception {
        CountDownLatch firstCallEnteredProvider = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        when(transactionRepository.findAll()).thenReturn(List.of(holding(QQQ)));
        when(timeProvider.today()).thenReturn(TODAY);
        when(signalProvider.fetchSnapshots(Set.of(QQQ), TODAY)).thenAnswer(invocation -> {
            firstCallEnteredProvider.countDown();
            releaseFirstCall.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RefreshOutcome> firstCall = pool.submit(useCase::execute);
            assertThat(firstCallEnteredProvider.await(5, TimeUnit.SECONDS)).isTrue();

            RefreshOutcome secondCallOutcome = useCase.execute();
            assertThat(secondCallOutcome).isEqualTo(new RefreshOutcome.AlreadyRunning());

            releaseFirstCall.countDown();
            assertThat(firstCall.get(5, TimeUnit.SECONDS)).isEqualTo(new RefreshOutcome.Completed(0));
        } finally {
            pool.shutdown();
        }
    }
}
