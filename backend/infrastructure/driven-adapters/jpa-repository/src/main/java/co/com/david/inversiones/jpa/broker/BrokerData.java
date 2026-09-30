package co.com.david.inversiones.jpa.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code brokers} table.
 *
 * <p>{@link co.com.david.inversiones.model.Transaction} carries its broker as a plain
 * {@code brokerName} string, but the schema stores it as a foreign key to this catalog table.
 * {@code TransactionRepositoryAdapter} resolves between the two, looking this entity up by name to
 * populate the foreign key on save and reading {@link #getName()} back to reconstitute the domain
 * field on load.
 */
@Entity
@Table(name = "brokers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;
}
