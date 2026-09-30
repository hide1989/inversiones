package co.com.david.inversiones.jpa.instrument;

import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code instruments} table: the read-only catalog of tradable instruments.
 *
 * <p>{@code type} and {@code currency} map to the domain's own {@link InstrumentType} and
 * {@link Currency} enums via {@link EnumType#STRING}, which is a direct, lossless match for the
 * table's {@code CHECK (type IN (...))} / {@code CHECK (currency IN (...))} constraints — no
 * hand-written conversion is needed for those two columns because there is nothing for a mapper to
 * get wrong. {@code ticker} still goes through {@code InstrumentMapper} to become a validated,
 * normalized {@link co.com.david.inversiones.model.Ticker} on the way out.
 */
@Entity
@Table(name = "instruments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstrumentData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private InstrumentType type;

    @Column(name = "sector", nullable = false)
    private String sector;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    // JdbcTypeCode(CHAR) matches the schema's CHAR(3): Hibernate's default mapping for a
    // string-valued column is VARCHAR, which schema validation rejects against the table's
    // bpchar column (ddl-auto is "validate", so the entity must match the migration exactly).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;
}
