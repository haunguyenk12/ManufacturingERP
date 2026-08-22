package com.erp.manufacturing.module.uom.service;

import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.repository.UomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only cross-module entry point for resolving global UOM codes in one query. */
@Service
@RequiredArgsConstructor
public class UomLookupService {

    private final UomRepository repository;

    @Transactional(readOnly = true)
    public Map<String, Uom> findByCode(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return repository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(Uom::getCode, Function.identity()));
    }
}
