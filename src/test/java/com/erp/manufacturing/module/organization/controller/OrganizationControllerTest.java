package com.erp.manufacturing.module.organization.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.organization.dto.CompanyCreateRequest;
import com.erp.manufacturing.module.organization.dto.CompanyResponse;
import com.erp.manufacturing.module.organization.dto.PlantCreateRequest;
import com.erp.manufacturing.module.organization.dto.PlantResponse;
import com.erp.manufacturing.module.organization.service.OrganizationService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link OrganizationController} (`D7b`, group B).
 *
 * <p>The other place `D7` named as still owing proof at the HTTP layer that
 * {@code OPERATION_NOT_ALLOWED} (422) stayed put: creating a plant under an <em>inactive company</em>
 * is master-data validation, which §5.3 keeps at 422 — it is not a document whose status forbids the
 * action. `D7` reviewed this module and changed <b>nothing</b>; this test is what makes that review
 * result durable.
 */
@WebMvcTest(controllers = OrganizationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OrganizationController – response envelope contract")
class OrganizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrganizationService organizationService;

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
    private static final UUID PLANT_ID   = UUID.randomUUID();

    private CompanyResponse sampleCompany() {
        return new CompanyResponse(COMPANY_ID, "COMP-01", "Acme Manufacturing", "ACTIVE",
                Instant.now(), Instant.now());
    }

    private PlantResponse samplePlant() {
        return new PlantResponse(PLANT_ID, COMPANY_ID, "PLANT-01", "Hanoi Plant",
                "Asia/Ho_Chi_Minh", "ACTIVE", Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("createCompany: valid request returns 201 with the ACTIVE company")
    void createCompany_validRequest_returns201Created() throws Exception {
        when(organizationService.createCompany(any(CompanyCreateRequest.class))).thenReturn(sampleCompany());

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"COMP-01","name":"Acme Manufacturing"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("COMP-01"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("createCompany: lowercase code violates the pattern and returns 400 with the field name")
    void createCompany_lowercaseCode_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"comp-01","name":"Acme Manufacturing"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(organizationService);
    }

    @Test
    @DisplayName("createCompany: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void createCompany_duplicateCode_returns409AlreadyExists() throws Exception {
        when(organizationService.createCompany(any(CompanyCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Company code already exists: COMP-01"));

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"COMP-01","name":"Acme Manufacturing"}
                                """))
                .andExpect(status().is(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.status().value()))
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()));
    }

    @Test
    @DisplayName("createPlant: inactive parent company stays 422 OPERATION_NOT_ALLOWED "
            + "(§5.3 — master data, not a document state conflict)")
    void createPlant_inactiveCompany_returns422OperationNotAllowed() throws Exception {
        when(organizationService.createPlant(eq(COMPANY_ID), any(PlantCreateRequest.class)))
                .thenThrow(new AppException(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Cannot create plant under inactive company: " + COMPANY_ID));

        mockMvc.perform(post("/api/v1/companies/" + COMPANY_ID + "/plants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PLANT-01","name":"Hanoi Plant","timezone":"Asia/Ho_Chi_Minh"}
                                """))
                .andExpect(status().is(BusinessErrorCode.OPERATION_NOT_ALLOWED.status().value()))
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.OPERATION_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("createPlant: valid request returns 201 with the plant under its company")
    void createPlant_validRequest_returns201Created() throws Exception {
        when(organizationService.createPlant(eq(COMPANY_ID), any(PlantCreateRequest.class)))
                .thenReturn(samplePlant());

        mockMvc.perform(post("/api/v1/companies/" + COMPANY_ID + "/plants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PLANT-01","name":"Hanoi Plant","timezone":"Asia/Ho_Chi_Minh"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.plantId").value(PLANT_ID.toString()))
                .andExpect(jsonPath("$.result.companyId").value(COMPANY_ID.toString()));
    }

    @Test
    @DisplayName("getCompany: malformed UUID in the path returns 400 VALIDATION_ERROR, not 500 "
            + "(§5.4 MethodArgumentTypeMismatchException handler)")
    void getCompany_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/companies/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));

        verifyNoInteractions(organizationService);
    }

    @Test
    @DisplayName("listCompanies: PageResult envelope (page/size/totalElements/totalPages/first/last) "
            + "is part of the contract")
    void listCompanies_returns200WithFullPageEnvelope() throws Exception {
        when(organizationService.listCompanies(any()))
                .thenReturn(new PageResult<>(List.of(sampleCompany()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].code").value("COMP-01"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    @DisplayName("deactivateCompany: returns 200 with a null result payload (§5.8 DELETE pattern)")
    void deactivateCompany_returns200NoContentEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/" + COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(organizationService).deactivateCompany(COMPANY_ID);
    }
}
