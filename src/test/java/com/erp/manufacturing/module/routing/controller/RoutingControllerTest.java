package com.erp.manufacturing.module.routing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.service.RoutingService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link RoutingController} (`D7`) – locks the {@code {code,result,message}}
 * envelope (`.claude/rules/error-handling.md` §5.1) and, most importantly, the <b>boundary</b>
 * between the two codes §5.3 keeps apart: a routing in the wrong <em>status</em> is 409
 * {@code STATE_CONFLICT}, while a routing whose <em>content</em> is incomplete stays 422
 * {@code OPERATION_NOT_ALLOWED}. Both branches are asserted here so a future "let's make it
 * consistent" pass cannot silently collapse them.
 */
@WebMvcTest(controllers = RoutingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RoutingController – response envelope contract")
class RoutingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RoutingService routingService;

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

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID    = UUID.randomUUID();
    private static final UUID ROUTING_ID = UUID.randomUUID();

    private RoutingResponse sampleResponse(String statusValue) {
        return new RoutingResponse(
                ROUTING_ID, COMPANY_ID, ITEM_ID, "FG-100", "Widget",
                "RT-001", "1", statusValue, null, Instant.now(), Instant.now(), List.of(), null, 1, null, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0L);
    }

    @Test
    @DisplayName("create: valid request returns 201 with the DRAFT routing")
    void create_validRequest_returns201Created() throws Exception {
        when(routingService.create(eq(COMPANY_ID), any(RoutingCreateRequest.class)))
                .thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(post("/v1/companies/" + COMPANY_ID + "/routings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","code":"RT-001","version":"1",
                                 "operations":[{"sequence":10,"name":"Assemble","workCenterId":"%s",
                                                "setupMinutes":5,"runMinutesPerUnit":2}]}
                                """.formatted(ITEM_ID, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.routingId").value(ROUTING_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.version").value("1"));
    }

    @Test
    @DisplayName("activate: routing that is not DRAFT returns 409 STATE_CONFLICT")
    void activate_routingNotDraft_returns409StateConflict() throws Exception {
        when(routingService.activate(eq(ROUTING_ID), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only DRAFT routings can be activated"));

        mockMvc.perform(post("/v1/routings/" + ROUTING_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("activate: routing without operations stays 422 OPERATION_NOT_ALLOWED (§5.3 — "
            + "incomplete content is not a status conflict)")
    void activate_routingWithoutOperations_returns422DocumentHasNoLines() throws Exception {
        when(routingService.activate(eq(ROUTING_ID), isNull()))
                .thenThrow(new AppException(BusinessErrorCode.DOCUMENT_HAS_NO_LINES,
                        "Cannot activate routing without operations"));

        mockMvc.perform(post("/v1/routings/" + ROUTING_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.DOCUMENT_HAS_NO_LINES.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.DOCUMENT_HAS_NO_LINES.code()));
    }

    @Test
    @DisplayName("deactivate: returns 200 with a null result payload (§5.8 DELETE pattern)")
    void deactivate_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/v1/routings/" + ROUTING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(routingService).deactivate(ROUTING_ID);
    }

    // ── G5 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT routing-drafts answers 200 with the saved graph")
    void saveDraft_returns200() throws Exception {
        when(routingService.saveDraft(eq(COMPANY_ID), any())).thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/v1/companies/" + COMPANY_ID + "/routing-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ROUTE-DEMO","itemId":"%s","operations":[{"clientKey":"assembly","sequence":1,
                                 "name":"Lắp ráp","workCenterId":"%s","setupMinutes":30,"runMinutesPerUnit":2,
                                 "predecessorClientKeys":[]}]}
                                """.formatted(ITEM_ID, java.util.UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.versionNumber").value(1));
    }

    @Test
    @DisplayName("PUT routing-drafts: runMinutesPerUnit 0 is 400 (must be positive on the new path)")
    void saveDraft_zeroRunMinutes_returns400() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/v1/companies/" + COMPANY_ID + "/routing-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ROUTE-DEMO","itemId":"%s","operations":[{"clientKey":"a","sequence":1,
                                 "name":"A","workCenterId":"%s","setupMinutes":0,"runMinutesPerUnit":0}]}
                                """.formatted(ITEM_ID, java.util.UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("operations[0].runMinutesPerUnit"));
        org.mockito.Mockito.verifyNoInteractions(routingService);
    }

    @Test
    @DisplayName("POST copy while a draft is open is 409 OPEN_DRAFT_EXISTS")
    void copy_openDraftExists_returns409() throws Exception {
        when(routingService.copy(eq(ROUTING_ID), any()))
                .thenThrow(new AppException(BusinessErrorCode.OPEN_DRAFT_EXISTS, "open draft"));

        mockMvc.perform(post("/v1/routings/" + ROUTING_ID + "/copy")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPEN_DRAFT_EXISTS.code()));
    }
}
