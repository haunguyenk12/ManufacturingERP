package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.dto.PurchaseRequisitionFromSuggestionRequest;
import com.erp.manufacturing.module.purchasing.dto.PurchaseRequisitionResponse;
import com.erp.manufacturing.module.purchasing.service.PurchaseRequisitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link PurchaseRequisitionController} (`D7`) – locks the
 * {@code {code,result,message}} envelope (`.claude/rules/error-handling.md` §5.1) and the two codes
 * {@code D7.3} retagged from 422 to 409 on the requisition lifecycle.
 *
 * <p>Permission checks (`@PreAuthorize`) live in the service layer (C1) and are covered by
 * {@code PurchasingMethodSecurityTest} – not re-tested here.
 */
@WebMvcTest(controllers = PurchaseRequisitionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PurchaseRequisitionController – response envelope contract")
class PurchaseRequisitionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PurchaseRequisitionService purchaseRequisitionService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // can be constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean
    IpExtractor ipExtractor;
    @MockBean
    JwtTokenProvider jwtTokenProvider;
    @MockBean
    TokenStoreService tokenStoreService;
    @MockBean
    UserDetailsService userDetailsService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;
    @MockBean
    RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID     = UUID.randomUUID();
    private static final UUID PLANT_ID       = UUID.randomUUID();
    private static final UUID REQUISITION_ID = UUID.randomUUID();
    private static final UUID SUGGESTION_ID  = UUID.randomUUID();

    private PurchaseRequisitionResponse sampleResponse(String statusValue) {
        return new PurchaseRequisitionResponse(
                REQUISITION_ID, COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                UUID.randomUUID(), "WH-01", "PR-001", statusValue, LocalDate.of(2026, 8, 1),
                "MRP", SUGGESTION_ID, null, Instant.now(), Instant.now(), List.of());
    }

    @Test
    @DisplayName("convert-to-purchase-requisition: approved suggestion returns 201 with the DRAFT requisition")
    void convertSuggestion_approvedSuggestion_returns201Created() throws Exception {
        when(purchaseRequisitionService.convertFromSuggestion(
                eq(SUGGESTION_ID), any(PurchaseRequisitionFromSuggestionRequest.class)))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/v1/supply-suggestions/" + SUGGESTION_ID + "/convert-to-purchase-requisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requisitionNo":"PR-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.purchaseRequisitionId").value(REQUISITION_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.sourceId").value(SUGGESTION_ID.toString()));
    }

    @Test
    @DisplayName("convert-to-purchase-requisition: suggestion not APPROVED returns 409 STATE_CONFLICT "
            + "(D7.3 retag, was 422)")
    void convertSuggestion_suggestionNotApproved_returns409StateConflict() throws Exception {
        when(purchaseRequisitionService.convertFromSuggestion(
                eq(SUGGESTION_ID), any(PurchaseRequisitionFromSuggestionRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only APPROVED supply suggestions can be converted"));

        mockMvc.perform(post("/v1/supply-suggestions/" + SUGGESTION_ID + "/convert-to-purchase-requisition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requisitionNo":"PR-001"}
                                """))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("approve: approving more than requested returns 409 PLANNED_QUANTITY_EXCEEDED "
            + "(D7.3 retag, was 422)")
    void approve_overRequestedQuantity_returns409PlannedQuantityExceeded() throws Exception {
        when(purchaseRequisitionService.approve(eq(REQUISITION_ID), any()))
                .thenThrow(new AppException(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED,
                        "Approved quantity cannot exceed requested quantity"));

        mockMvc.perform(post("/v1/purchase-requisitions/" + REQUISITION_ID + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionNote":"ok","approvedLines":[]}
                                """))
                .andExpect(status().is(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED.code()));
    }

    @Test
    @DisplayName("convert-to-purchase-order: requisition not APPROVED returns 409 STATE_CONFLICT "
            + "(D7.3 retag, was 422)")
    void convertToPurchaseOrder_requisitionNotApproved_returns409StateConflict() throws Exception {
        when(purchaseRequisitionService.convertToPurchaseOrder(eq(REQUISITION_ID), any()))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only APPROVED purchase requisitions can be converted to purchase orders"));

        mockMvc.perform(post("/v1/purchase-requisitions/" + REQUISITION_ID + "/convert-to-purchase-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purchaseOrderNo":"PO-001","orderDate":"2026-07-01","expectedDate":"2026-07-15"}
                                """))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(purchaseRequisitionService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("APPROVED")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/purchase-requisitions")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].requisitionNo").value("PR-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }
}
