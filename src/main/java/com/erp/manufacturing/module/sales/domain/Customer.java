package com.erp.manufacturing.module.sales.domain;

import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.organization.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Company-scoped customer master data (G2-1), the sales-side mirror of {@code Supplier}.
 *
 * <p>Not referenced by {@link SalesOrder} yet: {@code SalesOrder.customerName} stays free text until a
 * later phase decides how historical orders map onto customer rows.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_company_status", columnList = "company_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id", updatable = false, nullable = false)
    private UUID customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 255)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }

    public void activate() {
        status = CustomerStatus.ACTIVE;
    }

    public void deactivate() {
        status = CustomerStatus.INACTIVE;
    }
}
