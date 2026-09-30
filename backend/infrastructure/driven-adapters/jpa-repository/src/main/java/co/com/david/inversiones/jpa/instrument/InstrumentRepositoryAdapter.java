package co.com.david.inversiones.jpa.instrument;

import co.com.david.inversiones.model.Instrument;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link InstrumentRepository}.
 *
 * <p>Read-only: the instrument catalog is seeded by Flyway migrations, not written by the
 * application, so this adapter only ever queries {@link SpringDataInstrumentRepository}. No
 * {@link InstrumentData} is ever returned from a public method here; every result is converted to
 * the domain {@link Instrument} by {@link InstrumentMapper} before it leaves this class.
 */
@Repository
public class InstrumentRepositoryAdapter implements InstrumentRepository {

    private final SpringDataInstrumentRepository repository;

    public InstrumentRepositoryAdapter(SpringDataInstrumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Instrument> findByTicker(Ticker ticker) {
        return repository.findByTicker(ticker.symbol()).map(InstrumentMapper::toDomain);
    }

    @Override
    public List<Instrument> findAll() {
        return repository.findAll().stream().map(InstrumentMapper::toDomain).toList();
    }
}
