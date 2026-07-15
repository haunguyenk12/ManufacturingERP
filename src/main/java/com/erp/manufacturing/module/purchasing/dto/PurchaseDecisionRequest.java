package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PurchaseDecisionRequest(
        @Size(max = 2000) String decisionNote,
        List<@Valid PurchaseRequisitionLineApprovalRequest> approvedLines
) {}
