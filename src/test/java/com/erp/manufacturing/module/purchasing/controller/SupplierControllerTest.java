package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.purchasing.dto.ItemSupplierRequest;
import com.erp.manufacturing.module.purchasing.dto.ItemSupplierResponse;
import com.erp.manufacturing.module.purchasing.dto.SupplierCreateRequest;
import com.erp.manufacturing.module.purchasing.dto.SupplierResponse;
import com.erp.manufacturing.module.purchasing.service.SupplierService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link SupplierController} (`D7b`, group B).
 *
 * <p>This is one of the two places `D7` named as still owing proof that {@code OPERATION_NOT_ALLOWED}
 * (422) survives at the HTTP layer: invariant <b>B29</b> ("one active preferred supplier per item")
 * and "inactive supplier cannot be used" are both master-data validation, so §5.3 keeps them at 422
 * while every <em>status</em> check in this module moved to 409 {@code STATE_CONFLICT} in `D7`
 * (invariant B72). Both codes are asserted here so the boundary cannot collapse in either direction.
 */
@WebMvcTest(controllers = SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SupplierController – response envelope contract")
class SupplierControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SupplierService supplierService;

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

    private static final UUID COMPANY_ID       = UUID.randomUUID();
    private static final UUID SUPPLIER_ID      = UUID.randomUUID();
    private static final UUID ITEM_ID          = UUID.randomUUID();
    private static final UUID ITEM_SUPPLIER_ID = UUID.randomUUID();

    private SupplierResponse sampleSupplier(String statusValue) {
        return new SupplierResponse(SUPPLIER_ID, COMPANY_ID, "COMP-01", "SUP-001", "Acme Steel",
                "sales@acme.test", "0900000000", "1 Industrial Rd", "TAX-01",
                statusValue, Instant.now(), Instant.now());
    }

    private ItemSupplierResponse sampleItemSupplier() {
        return new ItemSupplierResponse(ITEM_SUPPLIER_ID, ITEM_ID, "MAT-001", "Steel Sheet",
                SUPPLIER_ID, "SUP-001", "Acme Steel", "ACME-SS-01",
                7, new BigDecimal("100.000000"), new BigDecimal("12.500000"), "USD",
                true, "ACTIVE", Instant.now(), Instant.now());
    }

    private String createBody() {
        return """
                {"companyId":"%s","code":"SUP-001","name":"Acme Steel","email":"sales@acme.test"}
                """.formatted(COMPANY_ID);
    }

    @Test
    @DisplayName("create: valid request returns 201 with the ACTIVE supplier")
    void create_validRequest_returns201Created() throws Exception {
        when(supplierService.create(any(SupplierCreateRequest.class))).thenReturn(sampleSupplier("ACTIVE"));

        mockMvc.perform(post("/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.supplierId").value(SUPPLIER_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("SUP-001"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("create: invalid email returns 400 VALIDATION_ERROR with the field name")
    void create_invalidEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","code":"SUP-001","name":"Acme Steel","email":"not-an-email"}
                                """.formatted(COMPANY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("create: duplicate supplier code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(supplierService.create(any(SupplierCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Supplier code already exists: SUP-001"));

        mockMvc.perform(post("/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("addItemSupplier: a second active preferred supplier stays 422 OPERATION_NOT_ALLOWED "
            + "(B29 — master-data rule, deliberately not promoted to 409 in `D7`)")
    void addItemSupplier_secondPreferred_returns422OperationNotAllowed() throws Exception {
        when(supplierService.addItemSupplier(eq(ITEM_ID), any(ItemSupplierRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Only one active preferred supplier is allowed for an item"));

        mockMvc.perform(post("/v1/items/" + ITEM_ID + "/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","preferred":true,"currencyCode":"USD"}
                                """.formatted(SUPPLIER_ID)))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("addItemSupplier: inactive supplier stays 422 OPERATION_NOT_ALLOWED (§5.3 master data)")
    void addItemSupplier_inactiveSupplier_returns422OperationNotAllowed() throws Exception {
        when(supplierService.addItemSupplier(eq(ITEM_ID), any(ItemSupplierRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Inactive supplier cannot be used: " + SUPPLIER_ID));

        mockMvc.perform(post("/v1/items/" + ITEM_ID + "/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","currencyCode":"USD"}
                                """.formatted(SUPPLIER_ID)))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()));
    }

    @Test
    @DisplayName("addItemSupplier: valid request returns 201 with the item supplier")
    void addItemSupplier_validRequest_returns201Created() throws Exception {
        when(supplierService.addItemSupplier(eq(ITEM_ID), any(ItemSupplierRequest.class)))
                .thenReturn(sampleItemSupplier());

        mockMvc.perform(post("/v1/items/" + ITEM_ID + "/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","preferred":true,"currencyCode":"USD","leadTimeDays":7}
                                """.formatted(SUPPLIER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.itemSupplierId").value(ITEM_SUPPLIER_ID.toString()))
                .andExpect(jsonPath("$.result.preferred").value(true))
                .andExpect(jsonPath("$.result.currencyCode").value("USD"));
    }

    @Test
    @DisplayName("list: PageResult envelope (page/size/totalElements/totalPages/first/last) is part of the contract")
    void list_returns200WithFullPageEnvelope() throws Exception {
        when(supplierService.list(eq(COMPANY_ID), eq(null), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(sampleSupplier("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/suppliers").param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("SUP-001"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        verify(supplierService).list(eq(COMPANY_ID), eq(null), eq(null), any());
    }
}
