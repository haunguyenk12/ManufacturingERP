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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuditLogQueryService")
class AuditLogQueryServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogChangeRepository auditLogChangeRepository = mock(AuditLogChangeRepository.class);
    private final AuditLogQueryService service =
            new AuditLogQueryService(auditLogRepository, auditLogChangeRepository, new ObjectMapper());

    private static final UUID AUDIT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("list: threads every filter through to the repository unchanged")
    void list_threadsEveryFilterThroughToTheRepository() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-06T00:00:00Z");
        when(auditLogRepository.search(
                eq(USER_ID), eq("WorkOrder"), eq("wo-1"), eq("WORK_ORDER_CREATED"),
                eq(PLANT_ID), eq("trace-1"), eq(from), eq(to), any()))
                .thenReturn(Page.empty());

        service.list(USER_ID, "WorkOrder", "wo-1", "WORK_ORDER_CREATED", PLANT_ID, "trace-1",
                from, to, PageRequest.of(0, 20));

        verify(auditLogRepository).search(
                eq(USER_ID), eq("WorkOrder"), eq("wo-1"), eq("WORK_ORDER_CREATED"),
                eq(PLANT_ID), eq("trace-1"), eq(from), eq(to), any());
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
        when(auditLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(auditLog)));

        var result = service.list(null, null, null, null, null, null, null, null, PageRequest.of(0, 20));

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
        assertThat(response.changes().get(0).oldValue().asText()).isEqualTo("DRAFT");
        assertThat(response.changes().get(0).newValue().asText()).isEqualTo("RELEASED");
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
