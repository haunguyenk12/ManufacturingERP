package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.module.planning.domain.MrpRunDemand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MrpRunDemandRepository extends JpaRepository<MrpRunDemand, UUID> {
}
