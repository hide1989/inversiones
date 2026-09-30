package co.com.david.inversiones.jpa.signal;

import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.model.SignalLevel;
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
 * JPA entity for the {@code signal_snapshots} table.
 *
 * <p>The six score columns are deliberately boxed {@link BigDecimal} fields (never primitives) so
 * that a {@code NULL} column and a stored {@code 0.00} remain distinguishable all the way up to
 * {@link SignalSnapshotMapper} — see that class for why collapsing the two would be a silent
 * scoring defect, not a cosmetic one.
 */
@Entity
@Table(name = "signal_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalSnapshotData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentData instrument;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "technical_score")
    private BigDecimal technicalScore;

    @Column(name = "news_score")
    private BigDecimal newsScore;

    @Column(name = "analyst_score")
    private BigDecimal analystScore;

    @Column(name = "insider_score")
    private BigDecimal insiderScore;

    @Column(name = "social_score")
    private BigDecimal socialScore;

    @Column(name = "wsb_score")
    private BigDecimal wsbScore;

    @Column(name = "combined_score")
    private BigDecimal combinedScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_level", length = 12)
    private SignalLevel signalLevel;

    /**
     * The raw provider payload. Captured verbatim so a future scoring change can be recomputed
     * over days already stored, but never parsed here: the adapter writes {@code null} for now
     * (the domain {@code SignalSnapshot} has no field for it) and ignores the column on read.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    /** Set by the database default; never written by this adapter. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
