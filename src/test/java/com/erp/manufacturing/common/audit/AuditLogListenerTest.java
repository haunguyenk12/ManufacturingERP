package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.context.RequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogListenerTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogChangeRepository changeRepository = mock(AuditLogChangeRepository.class);
    private final AuditLogListener listener = new AuditLogListener(auditLogRepository, changeRepository);

    @Test
    void handle_persistsFieldChangesWithGeneratedParentAuditId() {
        UUID auditId = UUID.randomUUID();
        when(auditLogRepository.saveAndFlush(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog submitted = invocation.getArgument(0);
            return AuditLog.builder()
                    .auditId(auditId)
                    .action(submitted.getAction())
                    .status(submitted.getStatus())
                    .build();
        });
        RequestContext context = new RequestContext(null, "admin", "127.0.0.1", null, "trace-1");
        AuditLogEvent event = new AuditLogEvent(
                context, "UOM_UPDATED", "Uom", UUID.randomUUID().toString(), "Piece updated",
                null, "SUCCESS",
                List.of(new AuditFieldChange(
                        "name", "\"Piece\"", "\"Piece updated\"", AuditLogChangeType.UPDATE)));

        listener.handle(event);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEntityName()).isEqualTo("Piece updated");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditLogChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(changeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(change -> {
            assertThat(change.getAuditId()).isEqualTo(auditId);
            assertThat(change.getFieldName()).isEqualTo("name");
            assertThat(change.getOldValue()).isEqualTo("\"Piece\"");
            assertThat(change.getNewValue()).isEqualTo("\"Piece updated\"");
        });
    }
}
