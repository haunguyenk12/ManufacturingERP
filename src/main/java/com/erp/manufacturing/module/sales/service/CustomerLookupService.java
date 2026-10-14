package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cross-module read entry point for customers (rule C7). Added for the spreadsheet importer (G3),
 * which must ask "which of these codes already exist?" in one query for a whole file (rule C14).
 */
@Service
@RequiredArgsConstructor
public class CustomerLookupService {

    private final CustomerRepository repository;

    /**
     * @return code (exactly as stored) → customer, any status; empty when {@code codes} is empty
     */
    @Transactional(readOnly = true)
    public Map<String, Customer> findCustomersByCode(UUID companyId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return repository.findByCompanyCompanyIdAndCodeIn(companyId, codes).stream()
                .collect(Collectors.toMap(Customer::getCode, Function.identity()));
    }
}
