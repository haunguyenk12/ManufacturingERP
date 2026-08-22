package com.erp.manufacturing.module.dataimport.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One uploaded spreadsheet and everything that happened to it.
 *
 * <p>Deliberately shaped like {@code MrpRun}: a status, summary counters, an optional
 * {@code Idempotency-Key} with a payload fingerprint. That similarity is the point — this repository
 * already has one "long operation you can look back at", and a second one that behaves differently
 * would be a second thing to learn.
 */
@Entity
@Table(name = "import_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "import_run_id", updatable = false, nullable = false)
    private UUID importRunId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private ImportTargetType targetType;

    /** Null while the user is still deciding which profile to validate against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private ImportProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    /**
     * Every header the file actually had, mapped or not. Keeping the unmapped ones is what lets the
     * mapping screen say "these three columns are being ignored" instead of dropping them in silence.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_headers", nullable = false, columnDefinition = "jsonb")
    private List<String> detectedHeaders;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportRunStatus status = ImportRunStatus.PARSING;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private Integer validRows = 0;

    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private Integer errorRows = 0;

    @Column(name = "applied_rows", nullable = false)
    @Builder.Default
    private Integer appliedRows = 0;

    @Column(name = "failed_rows", nullable = false)
    @Builder.Default
    private Integer failedRows = 0;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Client-supplied key on apply; {@code null} when the header was absent (it is optional). */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /** SHA-256 of the apply request, so the same key replayed with a different body is a 409. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    /**
     * Derives the human-facing run code from the identifier.
     *
     * <p>Must run as {@code @PrePersist} and not after {@code save()}: Hibernate snapshots the entity
     * when it queues the insert, so a field set afterwards is simply absent from the INSERT and the
     * NOT NULL column blows up (the lesson {@code MrpRun.assignCode} records as B68).
     */
    @PrePersist
    public void assignCode() {
        if (code == null) {
            code = "IMP-" + importRunId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
    }

    public void markValidated(Instant now, int total, int valid, int errors) {
        status = ImportRunStatus.VALIDATED;
        validatedAt = now;
        totalRows = total;
        validRows = valid;
        errorRows = errors;
        errorMessage = null;
    }

    public void markParsed(Instant now, int total) {
        status = ImportRunStatus.PARSED;
        parsedAt = now;
        totalRows = total;
    }

    public void startValidation() {
        status = ImportRunStatus.VALIDATING;
    }

    public void startApply() {
        status = ImportRunStatus.APPLYING;
    }

    /**
     * Records the outcome of apply. {@code FAILED} covers both "nothing was written" and "everything
     * that was attempted failed", so a run in that state never leaves the caller wondering whether
     * some rows landed.
     */
    public void markApplied(Instant now, int applied, int failed) {
        appliedAt = now;
        appliedRows = applied;
        failedRows = failed;
        if (applied == 0 && failed > 0) {
            status = ImportRunStatus.FAILED;
            errorMessage = "All valid rows failed during apply";
        } else if (failed > 0) {
            status = ImportRunStatus.PARTIALLY_APPLIED;
        } else {
            status = ImportRunStatus.APPLIED;
        }
    }

    public void cancel() {
        status = ImportRunStatus.CANCELLED;
    }

    public boolean canValidate() {
        return status == ImportRunStatus.PARSED || status == ImportRunStatus.VALIDATED;
    }

    public boolean canApply() {
        return status == ImportRunStatus.VALIDATED;
    }

    public boolean canCancel() {
        return !status.isTerminal();
    }
}
