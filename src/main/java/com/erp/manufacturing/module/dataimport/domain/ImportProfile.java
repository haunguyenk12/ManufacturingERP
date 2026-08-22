package com.erp.manufacturing.module.dataimport.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * A saved answer to "what do this customer's column names mean?".
 *
 * <p>The reason the importer could be built before any customer file existed: the part that varies
 * per customer lives here, as data. A different sheet is a different row, not a different class.
 *
 * <p>{@code mappings} is JSONB rather than a child table because it is always read and written whole,
 * is never queried by its contents, and is edited as one document by the mapping screen — a child
 * table would buy a join and nothing else.
 */
@Entity
@Table(name = "import_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id", updatable = false, nullable = false)
    private UUID profileId;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private ImportTargetType targetType;

    /** {@code null} makes the profile usable by every company — how the shipped default is stored. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** {@code null} means "first sheet", which is what a single-sheet export always is. */
    @Column(name = "sheet_name", length = 255)
    private String sheetName;

    /** 0-based, matching POI — not the 1-based row numbers Excel puts in the margin. */
    @Column(name = "header_row_index", nullable = false)
    @Builder.Default
    private Integer headerRowIndex = 0;

    @Column(name = "first_data_row_index", nullable = false)
    @Builder.Default
    private Integer firstDataRowIndex = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mappings", nullable = false, columnDefinition = "jsonb")
    private List<ColumnMapping> mappings;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportProfileStatus status = ImportProfileStatus.ACTIVE;

    public boolean isActive() {
        return status == ImportProfileStatus.ACTIVE;
    }

    public void activate() {
        status = ImportProfileStatus.ACTIVE;
    }

    public void deactivate() {
        status = ImportProfileStatus.INACTIVE;
    }
}
