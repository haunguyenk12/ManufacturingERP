package com.erp.manufacturing.module.purchasing.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_requisitions", indexes = {
        @Index(name = "idx_purchase_requisitions_company_plant_status_created", columnList = "company_id, plant_id, status, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "purchase_requisition_id", updatable = false, nullable = false)
    private UUID purchaseRequisitionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "requisition_no", nullable = false, length = 100)
    private String requisitionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseRequisitionStatus status = PurchaseRequisitionStatus.DRAFT;

    @Column(name = "needed_by_date", nullable = false)
    private LocalDate neededByDate;

    @Column(name = "source_type", length = 80)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @OneToMany(mappedBy = "purchaseRequisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseRequisitionLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return status == PurchaseRequisitionStatus.DRAFT;
    }

    public boolean isApproved() {
        return status == PurchaseRequisitionStatus.APPROVED;
    }

    public void approve(String note) {
        status = PurchaseRequisitionStatus.APPROVED;
        decisionNote = note;
    }

    public void reject(String note) {
        status = PurchaseRequisitionStatus.REJECTED;
        decisionNote = note;
    }

    public void cancel(String note) {
        status = PurchaseRequisitionStatus.CANCELLED;
        decisionNote = note;
    }

    public void markConverted() {
        status = PurchaseRequisitionStatus.CONVERTED;
    }
}
