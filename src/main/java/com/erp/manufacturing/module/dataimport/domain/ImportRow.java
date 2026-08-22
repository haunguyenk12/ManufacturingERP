package com.erp.manufacturing.module.dataimport.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the uploaded sheet, staged before anything is written to master data.
 *
 * <p>{@code rawCells} is JSONB keyed by the header text the customer typed, so a column the system
 * has never heard of is stored rather than rejected. That single decision is what let this table be
 * designed before any customer file existed.
 *
 * <p>Not a {@code BaseEntity}: a staging row is never edited by a user, has no meaningful "who
 * changed it last", and lives and dies with its run ({@code ON DELETE CASCADE}). Giving it an
 * optimistic-lock version would only add a column that nothing contends for.
 */
@Entity
@Table(name = "import_rows")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "import_row_id", updatable = false, nullable = false)
    private UUID importRowId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_run_id", nullable = false)
    private ImportRun importRun;

    /** 1-based, as Excel shows it, so an error names a row the user can navigate to. */
    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_cells", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rawCells;

    /**
     * Result of the mapping: target field name to value, still as text. Conversion to the target's
     * real types happens in the handler, at apply time, so everything up to that point stays
     * inspectable as the strings the user would recognise from their sheet.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapped_values", columnDefinition = "jsonb")
    private Map<String, String> mappedValues;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportRowStatus status = ImportRowStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<ImportCellError> errors;

    @Column(name = "created_entity_id")
    private UUID createdEntityId;

    public void markValid(Map<String, String> values) {
        mappedValues = values;
        status = ImportRowStatus.VALID;
        errors = null;
    }

    public void markInvalid(Map<String, String> values, List<ImportCellError> problems) {
        mappedValues = values;
        status = ImportRowStatus.ERROR;
        errors = problems;
    }

    public void markApplied(UUID entityId) {
        status = ImportRowStatus.APPLIED;
        createdEntityId = entityId;
        errors = null;
    }

    public void markFailed(ImportCellError problem) {
        status = ImportRowStatus.FAILED;
        errors = List.of(problem);
    }
}
