package com.erp.manufacturing.module.sales.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.dto.CustomerResponse;
import com.erp.manufacturing.module.sales.dto.CustomerUpdateRequest;
import com.erp.manufacturing.module.sales.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Contract test for {@link CustomerController} (G2-1): paths, status codes and the response envelope
 * FE §3.4 asked for. Business rules live in {@code CustomerServiceTest}; this class pins what the
 * HTTP layer adds — routing, binding, bean validation, and how service errors surface as {@code code}.
 */
@WebMvcTest(controllers = CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CustomerController – response envelope contract")
class CustomerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CustomerService customerService;

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

    private static final UUID COMPANY_ID  = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private CustomerResponse sample(String statusValue) {
        return new CustomerResponse(CUSTOMER_ID, COMPANY_ID, "PARTNER-01", "Demo Customer",
                "Demo Contact", "buyer@example.com", "0000123456", null, null,
                statusValue, Instant.parse("2026-10-14T08:00:00Z"), null);
    }

    @Test
    @DisplayName("create: 201 with the ACTIVE customer, companyId taken from the path")
    void create_validRequest_returns201AndUsesThePathCompany() throws Exception {
        when(customerService.create(eq(COMPANY_ID), any(CustomerCreateRequest.class))).thenReturn(sample("ACTIVE"));

        mockMvc.perform(post("/v1/companies/{companyId}/customers", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":" partner-01 ","name":"Demo Customer","contactName":"Demo Contact",
                                 "email":"buyer@example.com","phone":"0000123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.result.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.result.code").value("PARTNER-01"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                // Phone stays a string: leading zeros are data, not formatting.
                .andExpect(jsonPath("$.result.phone").value("0000123456"))
                .andExpect(jsonPath("$.result.createdAt").value("2026-10-14T08:00:00Z"));

        ArgumentCaptor<CustomerCreateRequest> captor = ArgumentCaptor.forClass(CustomerCreateRequest.class);
        verify(customerService).create(eq(COMPANY_ID), captor.capture());
        // The controller forwards the raw code; trim + uppercase is the service's job.
        assertThat(captor.getValue().code()).isEqualTo(" partner-01 ");
    }

    @Test
    @DisplayName("create: invalid email is a 400 field error and never reaches the service")
    void create_invalidEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/v1/companies/{companyId}/customers", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PARTNER-01","name":"Demo Customer","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("create: duplicate code returns 409 RESOURCE_ALREADY_EXISTS")
    void create_duplicateCode_returns409AlreadyExists() throws Exception {
        when(customerService.create(eq(COMPANY_ID), any(CustomerCreateRequest.class)))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Customer code already exists: PARTNER-01"));

        mockMvc.perform(post("/v1/companies/{companyId}/customers", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PARTNER-01","name":"Demo Customer"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code()));
    }

    @Test
    @DisplayName("list: forwards search/status, defaults sort to code asc with customerId as tie-breaker")
    void list_forwardsFiltersAndReturnsThePageEnvelope() throws Exception {
        when(customerService.list(eq(COMPANY_ID), eq(CustomerStatus.ACTIVE), eq("partner"), any(Pageable.class)))
                .thenReturn(new PageResult<>(List.of(sample("ACTIVE")), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/v1/companies/{companyId}/customers", COMPANY_ID)
                        .param("search", "partner")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.content[0].customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerService).list(eq(COMPANY_ID), eq(CustomerStatus.ACTIVE), eq("partner"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).containsExactly(
                Sort.Order.asc("code"), Sort.Order.asc("customerId"));
    }

    @Test
    @DisplayName("list: an unknown status value is a 400, not a 500")
    void list_unknownStatus_returns400() throws Exception {
        mockMvc.perform(get("/v1/companies/{companyId}/customers", COMPANY_ID)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("get: unknown customer returns 404 ENTITY_NOT_FOUND")
    void get_unknownCustomer_returns404() throws Exception {
        when(customerService.get(CUSTOMER_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));

        mockMvc.perform(get("/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("update: PATCH binds the body and returns the updated customer")
    void update_returns200() throws Exception {
        when(customerService.update(eq(CUSTOMER_ID), any(CustomerUpdateRequest.class))).thenReturn(sample("ACTIVE"));

        mockMvc.perform(patch("/v1/customers/{customerId}", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Demo Customer","notes":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("Demo Customer"));

        ArgumentCaptor<CustomerUpdateRequest> captor = ArgumentCaptor.forClass(CustomerUpdateRequest.class);
        verify(customerService).update(eq(CUSTOMER_ID), captor.capture());
        // Omitted stays null (keep), an empty string is forwarded as-is (clear) — the service decides.
        assertThat(captor.getValue().contactName()).isNull();
        assertThat(captor.getValue().notes()).isEmpty();
    }

    @Test
    @DisplayName("activate: inactive company surfaces as 422 RESOURCE_INACTIVE")
    void activate_underInactiveCompany_returns422ResourceInactive() throws Exception {
        when(customerService.activate(CUSTOMER_ID))
                .thenThrow(new AppException(BusinessErrorCode.RESOURCE_INACTIVE, "Company is inactive"));

        mockMvc.perform(post("/v1/customers/{customerId}/activate", CUSTOMER_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.RESOURCE_INACTIVE.code()));
    }

    @Test
    @DisplayName("deactivate: POST returns the INACTIVE customer")
    void deactivate_returns200WithInactiveStatus() throws Exception {
        when(customerService.deactivate(CUSTOMER_ID)).thenReturn(sample("INACTIVE"));

        mockMvc.perform(post("/v1/customers/{customerId}/deactivate", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
    }
}
