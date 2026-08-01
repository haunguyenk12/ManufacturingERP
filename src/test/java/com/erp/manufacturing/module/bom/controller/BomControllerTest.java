package com.erp.manufacturing.module.bom.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.bom.dto.BomCreateRequest;
import com.erp.manufacturing.module.bom.dto.BomLineCreateRequest;
import com.erp.manufacturing.module.bom.dto.BomLineUpdateRequest;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import com.erp.manufacturing.module.bom.service.BomService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link BomController} (`D7`) – locks the {@code {code,result,message}} envelope
 * (`.claude/rules/error-handling.md` §5.1). Two wire codes are asserted here for the first time at
 * the HTTP layer: {@code BOM_CIRCULAR_REFERENCE} (bất biến B8) and the {@code STATE_CONFLICT} that
 * {@code D7.3} retagged from 422 for non-DRAFT BOM edits (B11).
 *
 * <p>The 422 branch is asserted too: a {@code SERVICE} component is <em>master-data</em> validation
 * and deliberately stays {@code OPERATION_NOT_ALLOWED} (§5.3). Keeping both in one class makes an
 * accidental "consistency" sweep visible.
 */
@WebMvcTest(controllers = BomController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BomController – response envelope contract")
class BomControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BomService bomService;

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

    private static final UUID COMPANY_ID   = UUID.randomUUID();
    private static final UUID PARENT_ITEM   = UUID.randomUUID();
    private static final UUID COMPONENT_ITEM = UUID.randomUUID();
    private static final UUID BOM_ID       = UUID.randomUUID();
    private static final UUID LINE_ID      = UUID.randomUUID();

    private BomResponse sampleResponse(String statusValue) {
        return new BomResponse(
                BOM_ID, COMPANY_ID, PARENT_ITEM, "FG-100", "Widget", "REV-1", statusValue,
                null, Instant.now(), Instant.now(), List.of());
    }

    private String lineBody() {
        return """
                {"componentItemId":"%s","lineNo":10,"quantityPer":2,"scrapRate":0.1}
                """.formatted(COMPONENT_ITEM);
    }

    @Test
    @DisplayName("create: valid request returns 201 with the DRAFT BOM")
    void createBom_validRequest_returns201Created() throws Exception {
        when(bomService.createBom(eq(COMPANY_ID), any(BomCreateRequest.class)))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/api/v1/companies/" + COMPANY_ID + "/boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentItemId":"%s","revision":"REV-1"}
                                """.formatted(PARENT_ITEM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.bomId").value(BOM_ID.toString()))
                .andExpect(jsonPath("$.result.revision").value("REV-1"))
                .andExpect(jsonPath("$.result.status").value("DRAFT"));
    }

    @Test
    @DisplayName("activate: circular reference returns 422 BOM_CIRCULAR_REFERENCE (B8)")
    void activateBom_circularReference_returns422CircularReferenceCode() throws Exception {
        when(bomService.activateBom(BOM_ID))
                .thenThrow(new AppException(BusinessErrorCode.BOM_CIRCULAR_REFERENCE,
                        "Circular reference detected in Bill of Materials"));

        mockMvc.perform(post("/api/v1/boms/" + BOM_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.BOM_CIRCULAR_REFERENCE.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.BOM_CIRCULAR_REFERENCE.code()));
    }

    @Test
    @DisplayName("updateLine: non-DRAFT BOM returns 409 STATE_CONFLICT (B11, D7.3 retag from 422)")
    void updateLine_nonDraftBom_returns409StateConflict() throws Exception {
        when(bomService.updateLine(eq(LINE_ID), any(BomLineUpdateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only draft BOM can be changed"));

        mockMvc.perform(patch("/api/v1/bom-lines/" + LINE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineBody()))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("addLine: SERVICE component stays 422 OPERATION_NOT_ALLOWED (§5.3 — master-data "
            + "validation, not a status conflict)")
    void addLine_serviceComponent_returns422OperationNotAllowed() throws Exception {
        when(bomService.addLine(eq(BOM_ID), any(BomLineCreateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Service item cannot be used as a BOM component"));

        mockMvc.perform(post("/api/v1/boms/" + BOM_ID + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineBody()))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()));
    }
}
