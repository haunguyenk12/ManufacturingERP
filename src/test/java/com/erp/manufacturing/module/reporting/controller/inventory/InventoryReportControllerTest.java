package com.erp.manufacturing.module.reporting.controller.inventory;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.inventory.domain.InventoryAlertStatus;
import com.erp.manufacturing.module.inventory.dto.DashboardRecentMovementResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryAlertLineResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardResponse;
import com.erp.manufacturing.module.inventory.dto.InventoryDashboardShortageSummaryResponse;
import com.erp.manufacturing.module.inventory.service.InventoryAlertService;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link InventoryReportController} (`D7b`, group C).
 *
 * <p>Read-only reporting: no state machine, no idempotency. What matters on the wire is that the
 * dashboard keeps its nested shape (the summary object and the two line arrays are what the FE
 * renders) and that a bad enum or UUID in the query string is a 400, not a 500.
 */
@WebMvcTest(controllers = InventoryReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InventoryReportController – response envelope contract")
class InventoryReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryAlertService inventoryAlertService;

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

    private static final UUID SCOPE_ID   = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    private InventoryAlertLineResponse sampleAlertLine() {
        return new InventoryAlertLineResponse(
                UUID.randomUUID(), UUID.randomUUID(), "MAT-001", "Steel Sheet",
                UUID.randomUUID(), "WH-01", "Main Warehouse",
                new BigDecimal("25.000000"), new BigDecimal("40.000000"), 7,
                new BigDecimal("12.000000"), "REORDER_NEEDED",
                "PCS", new BigDecimal("15.000000"), new BigDecimal("3.000000"),
                BigDecimal.ZERO, new BigDecimal("28.000000"));
    }

    private DashboardRecentMovementResponse sampleMovement() {
        return new DashboardRecentMovementResponse(
                UUID.randomUUID(), "ISSUE", "OUT",
                UUID.randomUUID(), "MAT-001", "Steel Sheet", "PCS",
                UUID.randomUUID(), "WH-01", "Main Warehouse",
                null, null, new BigDecimal("20.000000"), "Issued to work order",
                "WORK_ORDER", UUID.randomUUID().toString(),
                UUID.randomUUID(), "operator.a", Instant.parse("2026-08-14T04:30:00Z"));
    }

    private InventoryDashboardResponse sampleDashboard() {
        return new InventoryDashboardResponse(
                "PLANT", SCOPE_ID, COMPANY_ID, 42, 3, 30L, 7L, 5L,
                new InventoryDashboardShortageSummaryResponse(
                        12, 7, 5, new BigDecimal("13.000000"), new BigDecimal("28.000000")),
                List.of(sampleAlertLine()), List.of(sampleMovement()),
                Instant.parse("2026-08-14T05:00:00Z"));
    }

    @Test
    @DisplayName("dashboard: returns 200 and keeps the nested summary shape the FE renders")
    void getDashboard_returns200WithNestedSummary() throws Exception {
        when(inventoryAlertService.getDashboard(eq(ScopeResourceType.PLANT), eq(SCOPE_ID), eq(null), eq(null)))
                .thenReturn(sampleDashboard());

        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.scopeType").value("PLANT"))
                .andExpect(jsonPath("$.result.totalItemCount").value(42))
                .andExpect(jsonPath("$.result.lowStockCount").value(7))
                .andExpect(jsonPath("$.result.reorderNeededCount").value(5))
                .andExpect(jsonPath("$.result.shortageSummary.alertLineCount").value(12))
                .andExpect(jsonPath("$.result.shortageSummary.safetyStockGapQuantity").value(13.000000))
                .andExpect(jsonPath("$.result.topLowStockLines[0].itemCode").value("MAT-001"));
    }

    /**
     * The dashboard is one aggregate read: every label the cards render has to be on the wire, or the
     * FE is back to one call per row. {@code generatedAt}/{@code createdAt} are asserted as ISO
     * strings rather than merely present — they were epoch numbers until the wire-format fix (§0.42).
     */
    @Test
    @DisplayName("dashboard: alert lines and movements carry their display labels, timestamps are ISO")
    void getDashboard_carriesDisplayLabelsAndIsoTimestamps() throws Exception {
        when(inventoryAlertService.getDashboard(eq(ScopeResourceType.PLANT), eq(SCOPE_ID), eq(null), eq(null)))
                .thenReturn(sampleDashboard());

        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.generatedAt").value("2026-08-14T05:00:00Z"))
                .andExpect(jsonPath("$.result.topLowStockLines[0].uomCode").value("PCS"))
                .andExpect(jsonPath("$.result.topLowStockLines[0].onHandQuantity").value(15.000000))
                .andExpect(jsonPath("$.result.topLowStockLines[0].reservedQuantity").value(3.000000))
                .andExpect(jsonPath("$.result.topLowStockLines[0].shortageQuantity").value(28.000000))
                .andExpect(jsonPath("$.result.recentMovements[0].itemCode").value("MAT-001"))
                .andExpect(jsonPath("$.result.recentMovements[0].itemName").value("Steel Sheet"))
                .andExpect(jsonPath("$.result.recentMovements[0].uomCode").value("PCS"))
                .andExpect(jsonPath("$.result.recentMovements[0].warehouseCode").value("WH-01"))
                .andExpect(jsonPath("$.result.recentMovements[0].warehouseName").value("Main Warehouse"))
                .andExpect(jsonPath("$.result.recentMovements[0].actorUsername").value("operator.a"))
                .andExpect(jsonPath("$.result.recentMovements[0].createdAt").value("2026-08-14T04:30:00Z"));
    }

    @Test
    @DisplayName("dashboard: limit params reach the service; absent ones stay null so the service "
            + "applies its own default")
    void getDashboard_forwardsTheLimitParams() throws Exception {
        when(inventoryAlertService.getDashboard(eq(ScopeResourceType.PLANT), eq(SCOPE_ID), any(), any()))
                .thenReturn(sampleDashboard());

        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString())
                        .param("lowStockLimit", "5")
                        .param("movementLimit", "3"))
                .andExpect(status().isOk());

        verify(inventoryAlertService).getDashboard(ScopeResourceType.PLANT, SCOPE_ID, 5, 3);
        verify(inventoryAlertService, never()).getDashboard(ScopeResourceType.PLANT, SCOPE_ID, null, null);
    }

    @Test
    @DisplayName("dashboard: a non-numeric limit is 400 VALIDATION_ERROR, not 500")
    void getDashboard_nonNumericLimit_returns400() throws Exception {
        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString())
                        .param("lowStockLimit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("lowStockLimit"));

        verifyNoInteractions(inventoryAlertService);
    }

    @Test
    @DisplayName("dashboard: unknown scope returns 404 ENTITY_NOT_FOUND")
    void getDashboard_unknownScope_returns404() throws Exception {
        when(inventoryAlertService.getDashboard(eq(ScopeResourceType.PLANT), eq(SCOPE_ID), eq(null), eq(null)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Plant not found: " + SCOPE_ID));

        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("dashboard: unknown scopeType enum value returns 400 VALIDATION_ERROR, not 500 "
            + "(§5.4 MethodArgumentTypeMismatchException handler)")
    void getDashboard_unknownScopeType_returns400() throws Exception {
        mockMvc.perform(get("/v1/reports/inventory-dashboard")
                        .param("scopeType", "GALAXY")
                        .param("scopeId", SCOPE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("scopeType"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(inventoryAlertService);
    }

    @Test
    @DisplayName("low-stock: returns 200 with the alert lines as a bare array (this endpoint is "
            + "deliberately not paginated — it returns one row per configured threshold)")
    void listAlerts_returns200WithAlertLines() throws Exception {
        when(inventoryAlertService.listAlerts(
                eq(ScopeResourceType.PLANT), eq(SCOPE_ID), eq(InventoryAlertStatus.REORDER_NEEDED)))
                .thenReturn(List.of(sampleAlertLine()));

        mockMvc.perform(get("/v1/reports/low-stock")
                        .param("scopeType", "PLANT")
                        .param("scopeId", SCOPE_ID.toString())
                        .param("status", "REORDER_NEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result[0].itemCode").value("MAT-001"))
                .andExpect(jsonPath("$.result[0].status").value("REORDER_NEEDED"))
                .andExpect(jsonPath("$.result[0].availableQuantity").value(12.000000));
    }

    @Test
    @DisplayName("low-stock: absent optional status filter reaches the service as null")
    void listAlerts_withoutStatusFilter_passesNullToService() throws Exception {
        when(inventoryAlertService.listAlerts(eq(ScopeResourceType.COMPANY), eq(SCOPE_ID), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/reports/low-stock")
                        .param("scopeType", "COMPANY")
                        .param("scopeId", SCOPE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").isArray());
    }
}
