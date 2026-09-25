package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuditLogQueryService")
class AuditLogQueryServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogChangeRepository auditLogChangeRepository = mock(AuditLogChangeRepository.class);
    private final AuditLogEntityRowRepository auditLogEntityRowRepository =
            mock(AuditLogEntityRowRepository.class);
    private final AuditLogQueryService service = new AuditLogQueryService(
            auditLogRepository, auditLogChangeRepository, auditLogEntityRowRepository, new ObjectMapper());

    private static final UUID AUDIT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    @DisplayName("list: threads every filter through to the repository unchanged")
    void list_threadsEveryFilterThroughToTheRepository() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-06T00:00:00Z");
        when(auditLogRepository.searchAdvanced(
                eq(USER_ID), eq("WorkOrder"), eq("wo-1"), eq("WORK_ORDER_CREATED"), eq("SUCCESS"),
                eq("HTTP"), eq(COMPANY_ID), eq(PLANT_ID), eq(WAREHOUSE_ID), eq("trace-1"),
                eq(from), eq(to), eq("Permission"), eq("perm-1"), any()))
                .thenReturn(Page.empty());

        service.list(AuditLogSearchCriteria.builder()
                .actorUserId(USER_ID).entityType("WorkOrder").entityId("wo-1")
                .action("WORK_ORDER_CREATED").outcome("SUCCESS").source("HTTP")
                .companyId(COMPANY_ID).plantId(PLANT_ID).warehouseId(WAREHOUSE_ID)
                .traceId("trace-1").from(from).to(to)
                .relatedEntityType("Permission").relatedEntityId("perm-1")
                .build(), PageRequest.of(0, 20));

        verify(auditLogRepository).searchAdvanced(
                eq(USER_ID), eq("WorkOrder"), eq("wo-1"), eq("WORK_ORDER_CREATED"), eq("SUCCESS"),
                eq("HTTP"), eq(COMPANY_ID), eq(PLANT_ID), eq(WAREHOUSE_ID), eq("trace-1"),
                eq(from), eq(to), eq("Permission"), eq("perm-1"), any());
    }

    @Test
    @DisplayName("list: maps each row to the summary response shape")
    void list_mapsRowsToSummaryShape() {
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .userId(USER_ID)
                .username("admin")
                .action("LOGIN")
                .entityName("Piece")
                .status("SUCCESS")
                .createdAt(Instant.parse("2026-08-06T10:00:00Z"))
                .build();
        when(auditLogRepository.searchAdvanced(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(auditLog)));

        var result = service.list(AuditLogSearchCriteria.builder().build(), PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        AuditLogResponse response = result.content().get(0);
        assertThat(response.auditId()).isEqualTo(AUDIT_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.action()).isEqualTo("LOGIN");
        assertThat(response.entityName()).isEqualTo("Piece");
        assertThat(response.plantId()).isNull();
    }

    @Test
    @DisplayName("get: unknown auditLogId throws RESOURCE_NOT_FOUND")
    void get_unknownId_throwsResourceNotFound() {
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AUDIT_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * The join against {@code audit_log_changes} is real, not a hardcoded {@code []} — this proves the
     * detail response reflects whatever the repository actually returns, so it will start showing real
     * data the moment a future phase writes to that table without any change here.
     */
    @Test
    @DisplayName("get: changes[] reflects whatever AuditLogChangeRepository returns, not a hardcoded empty list")
    void get_changesReflectsRepositoryResult_notAHardcodedEmptyList() {
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .action("WORK_ORDER_UPDATED")
                .entityName("WO-2026-001")
                .status("SUCCESS")
                .build();
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.of(auditLog));
        AuditLogChange change = AuditLogChange.builder()
                .changeId(UUID.randomUUID())
                .auditId(AUDIT_ID)
                .fieldName("status")
                .oldValue("\"DRAFT\"")
                .newValue("\"RELEASED\"")
                .changeType(AuditLogChangeType.UPDATE)
                .build();
        when(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(AUDIT_ID))
                .thenReturn(List.of(change));

        AuditLogDetailResponse response = service.get(AUDIT_ID);

        assertThat(response.changes()).hasSize(1);
        assertThat(response.entityName()).isEqualTo("WO-2026-001");
        assertThat(response.changes().get(0).fieldName()).isEqualTo("status");
        assertThat(response.changes().get(0).oldValue()).isEqualTo("DRAFT");
        assertThat(response.changes().get(0).newValue()).isEqualTo("RELEASED");
    }

    /**
     * Contract guard for {@code BACKEND_AUDIT_LOG_VALUE_CONTRACT_2026-08-25.md}: the storage column is
     * {@code jsonb}, so a snapshot can be any JSON shape, but the wire type must stay {@code String} or
     * {@code null} for every one of them. The object row is the exact case that crashed the frontend
     * audit drawer — it used to reach the client as a JSON object.
     */
    @Test
    @DisplayName("get: every jsonb shape (text, number, boolean, object, array, json null) is rendered as a String or null")
    void get_everyStoredJsonShape_isRenderedAsStringOrNull() {
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .action("WORK_ORDER_CREATED")
                .status("SUCCESS")
                .build();
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.of(auditLog));
        when(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(AUDIT_ID)).thenReturn(List.of(
                storedChange("componentItemCode", "\"D26-RM-CHAINRING\""),
                storedChange("requiredQuantity", "10"),
                storedChange("lotTracked", "true"),
                storedChange("componentRequirements",
                        "{\"uom\":\"EA\",\"lineNo\":1,\"requiredQuantity\":10}"),
                storedChange("messages", "[\"MATERIAL_SHORTAGE\"]"),
                storedChange("cancelReason", "null"),
                storedChange("legacyFreeText", "not json at all")));

        AuditLogDetailResponse response = service.get(AUDIT_ID);

        assertThat(response.changes()).extracting("fieldName", "newValue").containsExactly(
                tuple("componentItemCode", "D26-RM-CHAINRING"),
                tuple("requiredQuantity", "10"),
                tuple("lotTracked", "true"),
                tuple("componentRequirements", "{\"uom\":\"EA\",\"lineNo\":1,\"requiredQuantity\":10}"),
                tuple("messages", "[\"MATERIAL_SHORTAGE\"]"),
                tuple("cancelReason", null),
                tuple("legacyFreeText", "not json at all"));
    }

    @Test
    @DisplayName("get: a SQL NULL snapshot stays null, it is never turned into the text \"null\"")
    void get_sqlNullSnapshot_staysNull() {
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .action("WORK_ORDER_CREATED")
                .status("SUCCESS")
                .build();
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.of(auditLog));
        when(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(AUDIT_ID)).thenReturn(List.of(
                AuditLogChange.builder()
                        .changeId(UUID.randomUUID())
                        .auditId(AUDIT_ID)
                        .fieldName("workOrderNo")
                        .oldValue(null)
                        .newValue("\"WO-2026-001\"")
                        .changeType(AuditLogChangeType.CREATE)
                        .build()));

        AuditLogDetailResponse response = service.get(AUDIT_ID);

        assertThat(response.changes().get(0).oldValue()).isNull();
        assertThat(response.changes().get(0).newValue()).isEqualTo("WO-2026-001");
    }

    private AuditLogChange storedChange(String fieldName, String storedJson) {
        return AuditLogChange.builder()
                .changeId(UUID.randomUUID())
                .auditId(AUDIT_ID)
                .fieldName(fieldName)
                .newValue(storedJson)
                .changeType(AuditLogChangeType.CREATE)
                .build();
    }

    @Test
    @DisplayName("get: no changes recorded yet ⇒ changes[] is genuinely empty, not an error")
    void get_noChangesRecorded_changesIsEmpty() {
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .action("LOGIN")
                .status("SUCCESS")
                .build();
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.of(auditLog));
        when(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(AUDIT_ID)).thenReturn(List.of());

        AuditLogDetailResponse response = service.get(AUDIT_ID);

        assertThat(response.changes()).isEmpty();
    }
}
