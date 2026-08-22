package com.erp.manufacturing.module.sales.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.sales.dto.SalesOrderCreateRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderUpdateRequest;
import com.erp.manufacturing.module.sales.service.SalesOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link SalesOrderController} (`D7`) – locks the {@code {code,result,message}}
 * envelope (`.claude/rules/error-handling.md` §5.1) and the {@code X-Plant-Id} cross-check that
 * §5.6.1 says applies here because the request names {@code plantId} explicitly.
 *
 * <p>Permission checks (`@PreAuthorize`) live in the service layer (C1) and are covered by
 * {@code SalesOrderMethodSecurityTest} – not re-tested here.
 */
@WebMvcTest(controllers = SalesOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
// R3: PlantContextResolver holds the real header-vs-request comparison, so import the real bean
// instead of mocking it into a no-op.
@Import(PlantContextResolver.class)
@DisplayName("SalesOrderController – response envelope contract")
class SalesOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SalesOrderService salesOrderService;

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
    private static final UUID SALES_ORDER_ID = UUID.randomUUID();

    private SalesOrderResponse sampleResponse(String statusValue) {
        return sampleResponse(statusValue, 0L);
    }

    private SalesOrderResponse sampleResponse(String statusValue, long version) {
        return new SalesOrderResponse(
                SALES_ORDER_ID, COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                "SO-001", "ACME Corp", LocalDate.of(2026, 7, 1), statusValue, null,
                Instant.now(), Instant.now(), version, List.of());
    }

    private String createBody(UUID plantId) {
        return """
                {"companyId":"%s","plantId":"%s","orderNo":"SO-001","customerName":"ACME Corp",
                 "orderDate":"2026-07-01",
                 "lines":[{"itemId":"%s","orderedQuantity":10,"dueDate":"2026-08-01"}]}
                """.formatted(COMPANY_ID, plantId, UUID.randomUUID());
    }

    @Test
    @DisplayName("create: valid request returns 201 with the DRAFT order")
    void create_validRequest_returns201Created() throws Exception {
        when(salesOrderService.create(any(SalesOrderCreateRequest.class))).thenReturn(sampleResponse("DRAFT", 0L));

        mockMvc.perform(post("/sales-orders/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(PLANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.salesOrderId").value(SALES_ORDER_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.orderNo").value("SO-001"))
                .andExpect(jsonPath("$.result.version").value(0));
    }

    @Test
    @DisplayName("create: X-Plant-Id disagreeing with the body plantId returns 409 STATE_CONFLICT (§5.6.1)")
    void create_plantHeaderMismatch_returns409BeforeReachingService() throws Exception {
        mockMvc.perform(post("/sales-orders/v1")
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(PLANT_ID)))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        // R5: the cross-check must fail before any business logic runs
        verifyNoInteractions(salesOrderService);
    }

    @Test
    @DisplayName("update: valid request returns 200 with the updated order")
    void update_validRequest_returns200Ok() throws Exception {
        // version bumps from the expectedVersion sent (1) to 2 — response must reflect the NEW
        // value so the client has what it needs to send as expectedVersion on the next PATCH.
        when(salesOrderService.update(eq(SALES_ORDER_ID), any(SalesOrderUpdateRequest.class)))
                .thenReturn(sampleResponse("DRAFT", 2L));

        mockMvc.perform(patch("/sales-orders/v1/" + SALES_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"customerName":"New Customer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.salesOrderId").value(SALES_ORDER_ID.toString()))
                .andExpect(jsonPath("$.result.version").value(2));
    }

    @Test
    @DisplayName("update: missing expectedVersion returns 400 VALIDATION_ERROR naming the field")
    void update_missingExpectedVersion_returns400() throws Exception {
        mockMvc.perform(patch("/sales-orders/v1/" + SALES_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[?(@.field == 'expectedVersion')]").exists());

        verifyNoInteractions(salesOrderService);
    }

    @Test
    @DisplayName("update: stale expectedVersion returns 409 CONCURRENT_MODIFICATION")
    void update_staleExpectedVersion_returns409() throws Exception {
        when(salesOrderService.update(eq(SALES_ORDER_ID), any(SalesOrderUpdateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.CONCURRENT_MODIFICATION,
                        "Sales order was modified by another request"));

        mockMvc.perform(patch("/sales-orders/v1/" + SALES_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1}
                                """))
                .andExpect(status().is(BusinessErrorCode.CONCURRENT_MODIFICATION.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.CONCURRENT_MODIFICATION.code()));
    }

    @Test
    @DisplayName("confirm: order in a non-confirmable status returns 409 STATE_CONFLICT")
    void confirm_wrongStatus_returns409StateConflict() throws Exception {
        when(salesOrderService.confirm(SALES_ORDER_ID))
                .thenThrow(new AppException(BusinessErrorCode.STATE_CONFLICT,
                        "Only DRAFT sales orders can be confirmed"));

        mockMvc.perform(post("/sales-orders/v1/" + SALES_ORDER_ID + "/confirm"))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));
    }

    @Test
    @DisplayName("get: unknown sales order returns 404 ENTITY_NOT_FOUND")
    void get_unknownId_returns404() throws Exception {
        when(salesOrderService.get(SALES_ORDER_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Sales order not found with id: " + SALES_ORDER_ID));

        mockMvc.perform(get("/sales-orders/v1/" + SALES_ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(salesOrderService.list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleResponse("CONFIRMED")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/sales-orders/v1")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(salesOrderService).list(eq(COMPANY_ID), eq(PLANT_ID), eq(null), any());
    }
}
