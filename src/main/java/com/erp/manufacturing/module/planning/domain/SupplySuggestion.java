package com.erp.manufacturing.module.planning.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "supply_suggestions", indexes = {
        @Index(name = "idx_supply_suggestions_run_status_type", columnList = "mrp_run_id, status, suggestion_type"),
        @Index(name = "idx_supply_suggestions_item_status_needed", columnList = "item_id, status, needed_by_date"),
        @Index(name = "idx_supply_suggestions_requirement_line_id", columnList = "requirement_line_id"),
        @Index(name = "idx_supply_suggestions_run_exception_state", columnList = "mrp_run_id, exception_state")
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

    /**
     * Routing that was ACTIVE for the item when the run executed (spec §2.4 "Master data nguồn").
     * A frozen snapshot, exactly like {@code work_orders.source_routing_code} (invariant B49) — not
     * a live lookup, so a routing revised afterwards does not rewrite history.
     *
     * <p>Null on every BUY proposal, and on a MAKE proposal whose item has no ACTIVE routing — that
     * is the {@code BLOCKED}/{@code MISSING_ROUTING} case (B58/B59), not missing data.
     */
    @Column(name = "source_routing_code", length = 100)
    private String sourceRoutingCode;

    /** {@code RoutingHeader.routingVersion} — a String business version, not a number. */
    @Column(name = "source_routing_version", length = 40)
    private String sourceRoutingVersion;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "converted_reference_type", length = 80)
    private String convertedReferenceType;

    @Column(name = "converted_reference_id")
    private UUID convertedReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_state", nullable = false, length = 20)
    @Builder.Default
    private SupplySuggestionExceptionState exceptionState = SupplySuggestionExceptionState.READY;

    /**
     * {@link PlanningMessageCode} names joined by {@code ,}. A handful of codes per suggestion, only
     * ever read as a whole list, so a child table would buy nothing (see {@code messages()}).
     */
    @Column(name = "message_codes", length = 200)
    private String messageCodes;

    public List<String> messages() {
        if (!StringUtils.hasText(messageCodes)) {
            return List.of();
        }
        return List.of(messageCodes.split(","));
    }

    public boolean isBlocked() {
        return exceptionState == SupplySuggestionExceptionState.BLOCKED;
    }

    /**
     * The work order this suggestion became, or {@code null} — spec §2.4 wants the link typed so the
     * frontend can disable a second convert, while {@code convertedReferenceId} stays generic
     * because a BUY suggestion converts into a purchase requisition instead.
     */
    public UUID convertedWorkOrderId() {
        return "WORK_ORDER".equals(convertedReferenceType) ? convertedReferenceId : null;
    }

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
