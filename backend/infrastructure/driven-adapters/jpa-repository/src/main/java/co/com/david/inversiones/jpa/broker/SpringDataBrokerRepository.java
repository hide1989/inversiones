package co.com.david.inversiones.jpa.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@link BrokerData}. */
public interface SpringDataBrokerRepository extends JpaRepository<BrokerData, Long> {

    /** Looks a broker up by its exact catalog name. */
    Optional<BrokerData> findByName(String name);
}
