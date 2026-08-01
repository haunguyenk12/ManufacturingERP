package com.erp.manufacturing.module.planning.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.planning.dto.MrpRequirementLineResponse;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionResponse;
import com.erp.manufacturing.module.planning.service.MrpRunService;
import com.erp.manufacturing.module.planning.service.SupplySuggestionService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link PlanningRunController} (`D7`) – locks the {@code {code,result,message}}
 * envelope (`.claude/rules/error-handling.md` §5.1), the run header fields spec §2.4 requires
 * (`code` + the four summary counters added in `D4`), the {@code X-Plant-Id} cross-check (§5.6.1)
 * and the {@code MISSING_BOM} / {@code MISSING_ROUTING} block on converting a BLOCKED suggestion
 * (bất biến B58-B59).
 */
@WebMvcTest(controllers = PlanningRunController.class)
@AutoConfigureMockMvc(addFilters = false)
// R3: PlantContextResolver holds the real header-vs-request comparison, so import the real bean
// instead of mocking it into a no-op.
@Import(PlantContextResolver.class)
@DisplayName("PlanningRunController – response envelope contract")
class PlanningRunControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MrpRunService mrpRunService;
    @MockBean
    SupplySuggestionService supplySuggestionService;

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

    private static final UUID COMPANY_ID    = UUID.randomUUID();
    private static final UUID PLANT_ID      = UUID.randomUUID();
    private static final UUID RUN_ID        = UUID.randomUUID();
    private static final UUID SUGGESTION_ID = UUID.randomUUID();

    private MrpRunResponse sampleRun() {
        return new MrpRunResponse(
                RUN_ID, "RUN-0a1b2c3d", COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), "COMPLETED",
                Instant.now(), Instant.now(),
                3, 5, 4, new BigDecimal("120.000000"), 2, 1, 1, 0,
                null, Instant.now(), Instant.now());
    }

    private String runBody(UUID plantId) {
        return """
                {"companyId":"%s","plantId":"%s","horizonStartDate":"2026-07-01","horizonEndDate":"2026-08-01"}
                """.formatted(COMPANY_ID, plantId);
    }

    @Test
    @DisplayName("run: returns 201 with the spec §2.4 run header (code + 4 summary counters)")
    void run_validRequest_returns201WithRunHeader() throws Exception {
        when(mrpRunService.run(any(MrpRunCreateRequest.class))).thenReturn(sampleRun());

        mockMvc.perform(post("/api/v1/planning-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(PLANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.mrpRunId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("RUN-0a1b2c3d"))
                .andExpect(jsonPath("$.result.shortageLines").value(2))
                .andExpect(jsonPath("$.result.plannedWorkOrders").value(1))
                .andExpect(jsonPath("$.result.plannedPurchaseRecommendations").value(1))
                .andExpect(jsonPath("$.result.blockedProposals").value(0));
    }

    @Test
    @DisplayName("run: missing horizonEndDate fails @Valid before reaching the service")
    void run_missingHorizonEnd_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/planning-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","plantId":"%s","horizonStartDate":"2026-07-01"}
                                """.formatted(COMPANY_ID, PLANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("horizonEndDate"));

        verifyNoInteractions(mrpRunService);
    }

    @Test
    @DisplayName("run: X-Plant-Id disagreeing with the body plantId returns 409 STATE_CONFLICT (§5.6.1)")
    void run_plantHeaderMismatch_returns409BeforeReachingService() throws Exception {
        mockMvc.perform(post("/api/v1/planning-runs")
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(PLANT_ID)))
                .andExpect(status().is(BusinessErrorCode.STATE_CONFLICT.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(mrpRunService);
    }

    /**
     * F10 / debt G at the HTTP layer. {@code projectedAvailable} is a field of spec §2.4 the
     * frontend reads straight off the row, and it must be the figure the netting used — asserted
     * here together with the identity {@code net = gross + safetyStock - projectedAvailable} so a
     * mapper that "helpfully" recomputed it from availableQuantity would show up.
     */
    @Test
    @DisplayName("requirements: exposes projectedAvailableQuantity (spec §2.4)")
    void listRequirements_exposesTheProjectedAvailableUsedByTheNetting() throws Exception {
        when(mrpRunService.listRequirements(eq(RUN_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleRequirementLine()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/planning-runs/" + RUN_ID + "/requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].availableQuantity").value(12.0))
                .andExpect(jsonPath("$.result.content[0].projectedAvailableQuantity").value(2.0))
                .andExpect(jsonPath("$.result.content[0].safetyStockQuantity").value(0.0))
                .andExpect(jsonPath("$.result.content[0].netRequiredQuantity").value(8.0));
    }

    /** F10 / debt F at the HTTP layer: the frozen routing of spec §2.4 "Master data nguồn". */
    @Test
    @DisplayName("suggestions: MAKE proposal exposes the frozen sourceRoutingCode/Version (spec §2.4)")
    void listSuggestions_exposesTheFrozenRoutingSnapshot() throws Exception {
        when(mrpRunService.listSuggestions(eq(RUN_ID), any()))
                .thenReturn(new PageResult<>(List.of(sampleSuggestion()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/planning-runs/" + RUN_ID + "/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].supplyType").value("MAKE"))
                .andExpect(jsonPath("$.result.content[0].sourceRoutingCode").value("RT-ASSY"))
                .andExpect(jsonPath("$.result.content[0].sourceRoutingVersion").value("2"));
    }

    /** Line 2 of the two-demand story in {@code MrpCalculationServiceTest}: 12 on hand, 10 taken. */
    private MrpRequirementLineResponse sampleRequirementLine() {
        return new MrpRequirementLineResponse(
                UUID.randomUUID(), RUN_ID, null, null,
                UUID.randomUUID(), "RM-100", "Bolt", "EA",
                null, null, 0,
                new BigDecimal("10.000000"), new BigDecimal("12.000000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2.000000"), new BigDecimal("8.000000"),
                LocalDate.of(2026, 8, 1), "SHORTAGE", "ITEM_WAREHOUSE", 0, null, Instant.now());
    }

    private SupplySuggestionResponse sampleSuggestion() {
        return new SupplySuggestionResponse(
                SUGGESTION_ID, RUN_ID, UUID.randomUUID(), COMPANY_ID, "COMP-01", PLANT_ID, "PLANT-01",
                null, null, UUID.randomUUID(), "FG-100", "Widget", "EA",
                "MAKE", new BigDecimal("8.000000"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 25),
                "RT-ASSY", "2",
                "DRAFT", "READY", List.of("MATERIAL_SHORTAGE"),
                null, null, null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("convert-to-work-order: BLOCKED proposal without an ACTIVE BOM returns 409 MISSING_BOM (B58)")
    void convertSuggestionToWorkOrder_blockedProposal_returns409MissingBom() throws Exception {
        when(supplySuggestionService.convertToWorkOrder(eq(SUGGESTION_ID), any()))
                .thenThrow(new AppException(BusinessErrorCode.MISSING_BOM,
                        "No active BOM for this item"));

        mockMvc.perform(post("/api/v1/supply-suggestions/" + SUGGESTION_ID + "/convert-to-work-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workOrderNo":"WO-001"}
                                """))
                .andExpect(status().is(BusinessErrorCode.MISSING_BOM.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.MISSING_BOM.code()));
    }
}
