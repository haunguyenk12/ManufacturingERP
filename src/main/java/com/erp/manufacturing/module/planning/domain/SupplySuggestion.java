package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supply_suggestions", indexes = {
        @Index(name = "idx_supply_suggestions_run_status_type", columnList = "mrp_run_id, status, suggestion_type"),
        @Index(name = "idx_supply_suggestions_item_status_needed", columnList = "item_id, status, needed_by_date"),
        @Index(name = "idx_supply_suggestions_requirement_line_id", columnList = "requirement_line_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplySuggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "supply_suggestion_id", updatable = false, nullable = false)
    private UUID supplySuggestionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mrp_run_id", nullable = false)
    private MrpRun mrpRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_line_id", nullable = false)
    private MrpRequirementLine requirementLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false, length = 40)
    private SupplySuggestionType suggestionType;

    @Column(name = "suggested_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal suggestedQuantity;

    @Column(name = "needed_by_date", nullable = false)
    private LocalDate neededByDate;

    @Column(name = "suggested_order_date", nullable = false)
    private LocalDate suggestedOrderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SupplySuggestionStatus status = SupplySuggestionStatus.DRAFT;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "converted_reference_type", length = 80)
    private String convertedReferenceType;

    @Column(name = "converted_reference_id")
    private UUID convertedReferenceId;

    public boolean isDraft() {
        return status == SupplySuggestionStatus.DRAFT;
    }

    public boolean isApproved() {
        return status == SupplySuggestionStatus.APPROVED;
    }

    public void approve(String note) {
        status = SupplySuggestionStatus.APPROVED;
        decisionNote = note;
    }

    public void reject(String note) {
        status = SupplySuggestionStatus.REJECTED;
        decisionNote = note;
    }

    public void markConverted(String referenceType, UUID referenceId) {
        status = SupplySuggestionStatus.CONVERTED;
        convertedReferenceType = referenceType;
        convertedReferenceId = referenceId;
    }
}
