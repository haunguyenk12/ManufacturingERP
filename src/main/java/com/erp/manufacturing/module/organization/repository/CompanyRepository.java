package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByCode(String code);
}
