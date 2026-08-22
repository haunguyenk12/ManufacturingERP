package com.erp.manufacturing.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditableAspectTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuditChangeCaptureService captureService = mock(AuditChangeCaptureService.class);
    private final AuditableAspect aspect = new AuditableAspect(auditLogService, captureService);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void audit_success_capturesBeforeAndPublishesCalculatedChanges() throws Throwable {
        UUID id = UUID.randomUUID();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Auditable auditable = mock(Auditable.class);
        Object[] arguments = {id, new Object()};
        TestResponse response = new TestResponse(id, "Piece updated");
        Map<String, JsonNode> before = Map.of();
        List<AuditFieldChange> changes = List.of(new AuditFieldChange(
                "name", "\"Piece\"", "\"Piece updated\"", AuditLogChangeType.UPDATE));

        when(joinPoint.getArgs()).thenReturn(arguments);
        when(joinPoint.proceed()).thenReturn(response);
        when(auditable.action()).thenReturn(AuditAction.UOM_UPDATED);
        when(auditable.entityType()).thenReturn("Uom");
        when(auditable.entityIdExpression()).thenReturn("uomId.toString()");
        when(captureService.captureBefore("Uom", arguments, AuditAction.UOM_UPDATED)).thenReturn(before);
        when(captureService.resolveEntityName("Uom", before, response)).thenReturn("Piece updated");
        when(captureService.calculateChanges(AuditAction.UOM_UPDATED, before, response)).thenReturn(changes);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("clientIp", "127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.audit(joinPoint, auditable);

        verify(auditLogService).logEntity(
                any(), eq(AuditAction.UOM_UPDATED), eq("Uom"), eq(id.toString()),
                eq("Piece updated"), eq(changes));
    }

    private record TestResponse(UUID uomId, String name) {
    }
}
