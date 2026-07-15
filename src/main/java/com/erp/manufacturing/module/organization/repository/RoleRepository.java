package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("select r from Role r where r.name = :name and r.companyId is null")
    Optional<Role> findByName(@Param("name") String name);

    @Query("select count(r) > 0 from Role r where r.name = :name and r.companyId is null")
    boolean existsByName(@Param("name") String name);

    Optional<Role> findByCodeAndCompanyIdIsNull(String code);

    Optional<Role> findByCodeAndCompanyId(String code, UUID companyId);

    boolean existsByCodeAndCompanyIdIsNull(String code);

    boolean existsByCodeAndCompanyId(String code, UUID companyId);
}
