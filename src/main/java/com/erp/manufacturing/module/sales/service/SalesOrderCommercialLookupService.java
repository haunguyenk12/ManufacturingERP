package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * G10: entry point for {@code module/delivery} and {@code module/billing} into sales orders (rule C7). No
 * {@code @PreAuthorize}: every caller is itself a guarded method on the same plant.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderCommercialLookupService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;

    /** The order with its lines and their items, or empty — for guards, which must not throw through a proxy. */
    @Transactional(readOnly = true)
    public Optional<SalesOrder> findOrder(UUID salesOrderId) {
        return salesOrderId == null ? Optional.empty() : salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId);
    }

    /** Several orders with their lines and items, keyed by id — one query for a page of documents (C14). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, SalesOrder> findOrders(java.util.Collection<UUID> salesOrderIds) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return java.util.Map.of();
        }
        return salesOrderRepository.findWithLinesBySalesOrderIdIn(salesOrderIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrder::getSalesOrderId, order -> order, (a, b) -> a));
    }

    /** The order with its lines and their items. */
    @Transactional(readOnly = true)
    public SalesOrder getOrder(UUID salesOrderId) {
        return salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order", salesOrderId));
    }

    /**
     * Locks the order row, then re-reads it. Every command that claims or consumes quota on the order's lines takes
     * this lock first, so two of them queue instead of both reading the same remaining quantity. A native id lock,
     * then a plain refresh: with open-session-in-view the guard may already have loaded the order, and a locking query
     * on a managed entity only checks its version instead of re-reading it (B194).
     */
    @Transactional
    public SalesOrder lockOrder(UUID salesOrderId) {
        salesOrderRepository.lockRowBySalesOrderId(salesOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order", salesOrderId));
        SalesOrder order = getOrder(salesOrderId);
        entityManager.refresh(order);
        return order;
    }

    /** Bumps the order's version by exactly one: its lines are an inverse collection, so dirty the header (B112). */
    @Transactional
    public SalesOrder touch(SalesOrder order) {
        order.setUpdatedAt(Instant.now());
        return salesOrderRepository.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findCustomer(UUID customerId) {
        return customerId == null ? Optional.empty() : customerRepository.findWithCompanyByCustomerId(customerId);
    }
}
