package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportRowResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportRunResponse;
import com.erp.manufacturing.module.dataimport.service.ImportRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportRunController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportRunControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ImportRunService service;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.randomUUID();

    @Test
    void upload_returnsCreatedEnvelopeWithDetectedHeaders() throws Exception {
        when(service.upload(any(), eq(ImportTargetType.ITEM), isNull(), eq(COMPANY_ID), isNull(), isNull()))
                .thenReturn(run("PARSED"));
        MockMultipartFile file = new MockMultipartFile("file", "items.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/v1/import-runs")
                        .file(file)
                        .param("targetType", "ITEM")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.importRunId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.result.status").value("PARSED"))
                .andExpect(jsonPath("$.result.detectedHeaders[0]").value("Mã vật tư"));
    }

    @Test
    void upload_missingFile_returns400() throws Exception {
        mockMvc.perform(multipart("/v1/import-runs")
                        .param("targetType", "ITEM")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_invalidSpreadsheet_returnsStableValidationCode() throws Exception {
        when(service.upload(any(), eq(ImportTargetType.ITEM), isNull(), eq(COMPANY_ID), isNull(), isNull()))
                .thenThrow(new AppException(ValidationErrorCode.INVALID_INPUT, "Only .xlsx files are supported"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "items.txt", MediaType.TEXT_PLAIN_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/v1/import-runs")
                        .file(file)
                        .param("targetType", "ITEM")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
    }

    @Test
    void validate_andRowsExposePerRowMachineReadableErrorsInsideSuccessfulPayload() throws Exception {
        when(service.validate(eq(RUN_ID), any())).thenReturn(run("VALIDATED"));
        UUID rowId = UUID.randomUUID();
        ImportRowResponse row = new ImportRowResponse(rowId, 7, "ERROR",
                Map.of("Mã vật tư", ""), Map.of("code", ""),
                List.of(ImportCellError.of("Mã vật tư", "code", ImportErrorCode.REQUIRED_MISSING,
                        "Code is required")), null);
        when(service.rows(eq(RUN_ID), any(), any()))
                .thenReturn(new PageResult<>(List.of(row), 0, 20, 1, 1, true, true));

        mockMvc.perform(post("/v1/import-runs/{id}/validate", RUN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("VALIDATED"));

        mockMvc.perform(get("/v1/import-runs/{id}/rows", RUN_ID).param("status", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].rowNumber").value(7))
                .andExpect(jsonPath("$.result.content[0].errors[0].code").value("REQUIRED_MISSING"))
                .andExpect(jsonPath("$.result.content[0].errors[0].targetField").value("code"));
    }

    @Test
    void apply_forwardsOptionalIdempotencyHeader() throws Exception {
        when(service.apply(RUN_ID, "apply-1")).thenReturn(run("APPLIED"));

        mockMvc.perform(post("/v1/import-runs/{id}/apply", RUN_ID)
                        .header("Idempotency-Key", "apply-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPLIED"));

        verify(service).apply(RUN_ID, "apply-1");
    }

    private ImportRunResponse run(String status) {
        Instant now = Instant.now();
        return new ImportRunResponse(RUN_ID, "IMP-12345678", "ITEM", null, null,
                COMPANY_ID, null, null, "items.xlsx", 100L, "a".repeat(64),
                List.of("Mã vật tư"), List.of(), status,
                1, "VALIDATED".equals(status) || "APPLIED".equals(status) ? 1 : 0,
                0, "APPLIED".equals(status) ? 1 : 0, 0,
                now, "PARSED".equals(status) ? null : now,
                "APPLIED".equals(status) ? now : null, null, now);
    }
}
