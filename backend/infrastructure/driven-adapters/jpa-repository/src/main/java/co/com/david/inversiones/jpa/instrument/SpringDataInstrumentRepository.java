package co.com.david.inversiones.jpa.instrument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@link InstrumentData}. */
public interface SpringDataInstrumentRepository extends JpaRepository<InstrumentData, Long> {

    /** Looks an instrument up by its exact, normalized ticker symbol. */
    Optional<InstrumentData> findByTicker(String ticker);
}
