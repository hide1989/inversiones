package co.com.david.inversiones.jpa.transaction;

import co.com.david.inversiones.jpa.broker.BrokerData;
import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * JPA entity for the {@code transactions} table.
 *
 * <p>The {@code amount}/{@code currency} split the schema uses for money is exactly why this
 * adapter maps by hand rather than through a reflection-based mapper: {@code unitPrice} and
 * {@code fees} are plain {@link BigDecimal} columns here, and only {@code TransactionMapper}
 * reunites each with the single {@code currency} column to reconstitute a domain
 * {@link co.com.david.inversiones.model.Money}. A generic mapper working field name to field name
 * has no way to know that two numeric columns and one string column together mean two monetary
 * amounts — it would either drop the currency silently or fail confusingly. {@code instrument} and
 * {@code broker} are real {@code @ManyToOne} associations (not raw id columns) so that
 * {@code SpringDataTransactionRepository} can derive {@code findByInstrumentTickerAndBrokerName} as
 * a single indexed join, instead of the adapter loading every row and filtering in Java.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentData instrument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_id", nullable = false)
    private BrokerData broker;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "fees", nullable = false)
    private BigDecimal fees;

    /**
     * Currency both {@link #unitPrice} and {@link #fees} are denominated in.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.CHAR)} matches the schema's CHAR(3): Hibernate's default
     * mapping for a string-valued column is VARCHAR, which schema validation rejects against the
     * table's bpchar column ({@code ddl-auto} is {@code validate}, so the entity must match the
     * migration exactly).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "traded_at", nullable = false)
    private LocalDate tradedAt;

    /** Set by the database default; never written by this adapter. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
