package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only cross-module entry point for resolving spreadsheet supplier codes in one query. */
@Service
@RequiredArgsConstructor
public class SupplierLookupService {

    private final SupplierRepository repository;

    @Transactional(readOnly = true)
    public Map<String, Supplier> findSuppliersByCode(UUID companyId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return repository.findByCompanyCompanyIdAndCodeIn(companyId, codes).stream()
                .collect(Collectors.toMap(Supplier::getCode, Function.identity()));
    }
}
