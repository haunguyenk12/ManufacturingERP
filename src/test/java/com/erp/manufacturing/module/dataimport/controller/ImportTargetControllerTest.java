package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportFieldResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportTargetResponse;
import com.erp.manufacturing.module.dataimport.service.ImportTargetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of {@link ImportTargetController}; the service is mocked. */
@WebMvcTest(controllers = ImportTargetController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportTargetControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ImportTargetService service;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID = UUID.randomUUID();

    private static ImportTargetResponse itemTarget() {
        return new ImportTargetResponse("ITEM", "Vật tư", List.of(
                new ImportFieldResponse("code", "Mã vật tư", "STRING", true, 100, null, "RM-001")));
    }

    @Test
    void list_returnsEveryTargetInsideTheSuccessEnvelope() throws Exception {
        when(service.list(COMPANY_ID)).thenReturn(List.of(itemTarget()));

        mockMvc.perform(get("/v1/import-targets").param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result[0].type").value("ITEM"))
                .andExpect(jsonPath("$.result[0].fields[0].name").value("code"))
                .andExpect(jsonPath("$.result[0].fields[0].required").value(true));
    }

    @Test
    void fields_withoutCompany_passesNullAndReturnsTheTarget() throws Exception {
        when(service.get(eq(ImportTargetType.ITEM), isNull())).thenReturn(itemTarget());

        mockMvc.perform(get("/v1/import-targets/{type}/fields", "ITEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.label").value("Vật tư"))
                .andExpect(jsonPath("$.result.fields[0].maxLength").value(100));
    }

    @Test
    void fields_unknownType_returns400NotAServerError() throws Exception {
        mockMvc.perform(get("/v1/import-targets/{type}/fields", "NOT_A_TARGET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(service);
    }

    @Test
    void template_streamsTheWorkbookAsAnXlsxAttachmentNamedAfterTheTarget() throws Exception {
        byte[] workbook = {0x50, 0x4B, 0x03, 0x04};
        when(service.template(ImportTargetType.ITEM, COMPANY_ID)).thenReturn(workbook);

        mockMvc.perform(get("/v1/import-targets/{type}/template", "ITEM")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("import-item.xlsx")))
                .andExpect(content().bytes(workbook));
    }
}
