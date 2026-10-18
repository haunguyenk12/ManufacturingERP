package com.erp.manufacturing.module.dataimport.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ResultCarryingException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchRowResponse;
import com.erp.manufacturing.module.dataimport.dto.DataImportIssueResponse;
import com.erp.manufacturing.module.dataimport.service.DataImportBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataImportBatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PlantContextResolver.class)
class DataImportBatchControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DataImportBatchService service;

    @MockBean IpExtractor ipExtractor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean RateLimitProperties rateLimitProperties;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "items-vi.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

    /** FE Q6: a file with row issues is still a successful validation — 201 with an INVALID batch. */
    @Test
    void upload_invalidRows_returns201WithTheIssuesInTheBatch() throws Exception {
        when(service.upload(any(), eq(DataImportKind.ITEMS), eq(COMPANY_ID), eq(PLANT_ID), eq("KEY-1")))
                .thenReturn(batch("INVALID", List.of(new DataImportIssueResponse(
                        "unit", "D", "REFERENCE_NOT_FOUND", "Unit of measure 'XX' does not exist"))));

        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "items")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .header("Idempotency-Key", "KEY-1")
                        .header(PlantContextResolver.HEADER, PLANT_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.kind").value("items"))
                .andExpect(jsonPath("$.result.status").value("INVALID"))
                .andExpect(jsonPath("$.result.rows[0].line").value(2))
                .andExpect(jsonPath("$.result.rows[0].issues[0].column").value("D"))
                .andExpect(jsonPath("$.result.rows[0].issues[0].code").value("REFERENCE_NOT_FOUND"))
                .andExpect(jsonPath("$.result.createdIds").isEmpty());
    }

    /** Partner batches ignore a plant even when the UI sends its switcher's value. */
    @Test
    void upload_partnerKind_dropsThePlant() throws Exception {
        when(service.upload(any(), eq(DataImportKind.CUSTOMERS), eq(COMPANY_ID), isNull(), isNull()))
                .thenReturn(batch("READY", List.of()));

        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "customers")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isCreated());

        verify(service).upload(any(), eq(DataImportKind.CUSTOMERS), eq(COMPANY_ID), isNull(), isNull());
    }

    @Test
    void upload_unknownKind_returns400NamingTheField_andNeverReachesTheService() throws Exception {
        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "boms")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors[0].field").value("kind"));

        verifyNoInteractions(service);
    }

    @Test
    void upload_itemsWithoutPlant_returns400_notA403() throws Exception {
        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "items")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("plantId"));

        verifyNoInteractions(service);
    }

    /** G6c: sales orders are plant-scoped like items — no plant is a 400 naming it; with one it is forwarded. */
    @Test
    void upload_salesOrders_requireAndForwardThePlant() throws Exception {
        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "so")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("plantId"))
                .andExpect(jsonPath("$.errors[0].message").value("is required when kind is so"));
        verifyNoInteractions(service);

        when(service.upload(any(), eq(DataImportKind.SALES_ORDERS), eq(COMPANY_ID), eq(PLANT_ID), isNull()))
                .thenReturn(batch("READY", List.of()));
        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "so")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString()))
                .andExpect(status().isCreated());
        verify(service).upload(any(), eq(DataImportKind.SALES_ORDERS), eq(COMPANY_ID), eq(PLANT_ID), isNull());
    }

    @Test
    void upload_plantHeaderDisagreesWithTheRequest_returns409StateConflict() throws Exception {
        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "items")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .header(PlantContextResolver.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.STATE_CONFLICT.code()));

        verifyNoInteractions(service);
    }

    @Test
    void upload_fileLevelRefusal_keepsItsOwnCode() throws Exception {
        when(service.upload(any(), any(), any(), any(), any()))
                .thenThrow(new AppException(ValidationErrorCode.FILE_SIZE, "File exceeds 5242880 bytes"));

        mockMvc.perform(multipart("/v1/data-import/batches").file(FILE)
                        .param("kind", "suppliers")
                        .param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.FILE_SIZE.code()));
    }

    @Test
    void commit_returnsTheCommittedBatch_andForwardsTheKey() throws Exception {
        when(service.commit(BATCH_ID, 0L, "COMMIT-1")).thenReturn(batch("COMMITTED", List.of()));

        mockMvc.perform(post("/v1/data-import/batches/{id}/commit", BATCH_ID)
                        .header("Idempotency-Key", "COMMIT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMMITTED"));
    }

    @Test
    void commit_withoutExpectedVersion_returns400() throws Exception {
        mockMvc.perform(post("/v1/data-import/batches/{id}/commit", BATCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));

        verifyNoInteractions(service);
    }

    /** FE Q6: the stale-commit 409 carries the batch, now INVALID, in {@code result}. */
    @Test
    void commit_staleBatch_returns409WithTheInvalidBatchInResult() throws Exception {
        when(service.commit(BATCH_ID, 0L, null)).thenThrow(new ResultCarryingException(
                BusinessErrorCode.IMPORT_REVALIDATION_FAILED, "Import batch is no longer valid; nothing was created",
                batch("INVALID", List.of(new DataImportIssueResponse("code", "A", "ALREADY_EXISTS", "taken")))));

        mockMvc.perform(post("/v1/data-import/batches/{id}/commit", BATCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BusinessErrorCode.IMPORT_REVALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.result.status").value("INVALID"))
                .andExpect(jsonPath("$.result.rows[0].issues[0].code").value("ALREADY_EXISTS"));
    }

    @Test
    void list_forwardsFiltersAndNewestFirstPaging() throws Exception {
        when(service.list(eq(COMPANY_ID), eq(PLANT_ID), eq(DataImportKind.ITEMS), eq(ImportRunStatus.READY),
                eq("vat-tu"), any())).thenReturn(PageResult.from(new PageImpl<>(List.of(), Pageable.ofSize(20), 0)));

        mockMvc.perform(get("/v1/data-import/batches")
                        .param("companyId", COMPANY_ID.toString())
                        .param("plantId", PLANT_ID.toString())
                        .param("kind", "items")
                        .param("status", "READY")
                        .param("search", "vat-tu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));

        verify(service).list(eq(COMPANY_ID), eq(PLANT_ID), eq(DataImportKind.ITEMS), eq(ImportRunStatus.READY),
                eq("vat-tu"), argThat(pageable -> pageable.getPageSize() == 20
                        && pageable.getSort().getOrderFor("createdAt").isDescending()
                        && pageable.getSort().getOrderFor("importRunId") != null));
    }

    /** Statuses of the mapped-profile path are not batch statuses. */
    @Test
    void list_statusOfTheOtherPath_returns400() throws Exception {
        mockMvc.perform(get("/v1/data-import/batches")
                        .param("companyId", COMPANY_ID.toString())
                        .param("status", "APPLIED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verifyNoInteractions(service);
    }

    @Test
    void get_unknownBatch_returns404() throws Exception {
        when(service.get(BATCH_ID)).thenThrow(new AppException(ValidationErrorCode.RESOURCE_NOT_FOUND, "Import batch"));

        mockMvc.perform(get("/v1/data-import/batches/{id}", BATCH_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    private static DataImportBatchResponse batch(String status, List<DataImportIssueResponse> issues) {
        return new DataImportBatchResponse(BATCH_ID, UUID.randomUUID(), COMPANY_ID, PLANT_ID, DataImportKind.ITEMS,
                "vi", "omniplant-import-v1", "items-vi.xlsx", 18200, status, 0, Instant.now(), null,
                1, issues.isEmpty() ? 1 : 0, issues.isEmpty() ? 0 : 1, 0,
                List.of(new DataImportBatchRowResponse(2, Map.of("code", "GD3-RM-0001"), issues)), List.of(), null, null);
    }
}
