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
                "RT-001", "1", statusValue, null, Instant.now(), Instant.now(), List.of());
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
        when(routingService.activate(ROUTING_ID))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only DRAFT routings can be activated"));

        mockMvc.perform(post("/v1/routings/" + ROUTING_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("activate: routing without operations stays 422 OPERATION_NOT_ALLOWED (§5.3 — "
            + "incomplete content is not a status conflict)")
    void activate_routingWithoutOperations_returns422OperationNotAllowed() throws Exception {
        when(routingService.activate(ROUTING_ID))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Cannot activate routing without operations"));

        mockMvc.perform(post("/v1/routings/" + ROUTING_ID + "/activate"))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()));
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
}
