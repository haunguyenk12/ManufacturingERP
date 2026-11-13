package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileResponse;
import com.erp.manufacturing.module.dataimport.service.ImportProfileService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of {@link ImportProfileController}; the service is mocked, permissions live there. */
@WebMvcTest(controllers = ImportProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ImportProfileService service;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    private static ImportProfileResponse profile() {
        return new ImportProfileResponse(PROFILE_ID, "ITEM-STD", "Standard items", "ITEM", COMPANY_ID,
                "Sheet1", 0, 1,
                List.of(new ColumnMapping("Mã vật tư", "code", List.of("TRIM"), null)),
                "ACTIVE", Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static String createBody() {
        return """
                {"code":"ITEM-STD","name":"Standard items","targetType":"ITEM","companyId":"%s",
                 "mappings":[{"sourceHeader":"Mã vật tư","targetField":"code","transforms":["TRIM"]}]}
                """.formatted(COMPANY_ID);
    }

    @Test
    void create_returnsCreatedEnvelopeWithTheProfile() throws Exception {
        when(service.create(any())).thenReturn(profile());

        mockMvc.perform(post("/v1/import-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.result.targetType").value("ITEM"))
                .andExpect(jsonPath("$.result.mappings[0].targetField").value("code"));
    }

    @Test
    void create_withoutMappings_returns400NamingTheFieldAndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/v1/import-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ITEM-STD","name":"Standard items","targetType":"ITEM","companyId":"%s",
                                 "mappings":[]}
                                """.formatted(COMPANY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("mappings"));

        verifyNoInteractions(service);
    }

    @Test
    void list_returnsThePageEnvelopeAndPassesFiltersAndDefaultPaging() throws Exception {
        when(service.list(eq(COMPANY_ID), eq(ImportTargetType.ITEM), eq(ImportProfileStatus.ACTIVE), any()))
                .thenReturn(new PageResult<>(List.of(profile()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/v1/import-profiles")
                        .param("companyId", COMPANY_ID.toString())
                        .param("targetType", "ITEM")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].code").value("ITEM-STD"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(eq(COMPANY_ID), eq(ImportTargetType.ITEM), eq(ImportProfileStatus.ACTIVE),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("code").ascending());
    }

    @Test
    void list_capsAnOversizedPageAtOneHundred() throws Exception {
        when(service.list(any(), isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0, 0, true, true));

        mockMvc.perform(get("/v1/import-profiles")
                        .param("companyId", COMPANY_ID.toString())
                        .param("size", "5000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(eq(COMPANY_ID), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void list_withoutCompanyId_returns400NamingTheParameter() throws Exception {
        mockMvc.perform(get("/v1/import-profiles"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));

        verifyNoInteractions(service);
    }

    @Test
    void get_unknownProfile_returns404() throws Exception {
        when(service.get(PROFILE_ID))
                .thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Import profile not found"));

        mockMvc.perform(get("/v1/import-profiles/{id}", PROFILE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    void update_returnsTheUpdatedProfile() throws Exception {
        when(service.update(eq(PROFILE_ID), any())).thenReturn(profile());

        mockMvc.perform(patch("/v1/import-profiles/{id}", PROFILE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Standard items",
                                 "mappings":[{"sourceHeader":"Mã vật tư","targetField":"code"}],
                                 "status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }
}
