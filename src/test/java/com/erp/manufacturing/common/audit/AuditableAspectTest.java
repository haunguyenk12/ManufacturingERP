package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.context.AuditContext;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditableAspectTest {

    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final AuditChangeCaptureService captureService = mock(AuditChangeCaptureService.class);
    private final AuditExpressionEvaluator evaluator = new AuditExpressionEvaluator();
    private final AuditableAspect aspect = new AuditableAspect(
            auditRecorder, captureService, evaluator, List.of(), List.of());

    // ── Fixture ──────────────────────────────────────────────────────────

    /** Stands in for an audited service; used only for its reflective signature. */
    @SuppressWarnings("unused")
    static class SampleService {
        public TestResponse update(UUID uomId, String name) {
            return null;
        }

        public void release(UUID workOrderId) {
        }
    }

    private record TestResponse(UUID uomId, String name, UUID plantId) {
    }

    private void stubRecorderDraft() {
        when(auditRecorder.draft(any())).thenAnswer(invocation ->
                AuditRecordDraft.builder().action(invocation.getArgument(0)));
        when(auditRecorder.currentContext()).thenReturn(AuditContext.system("test"));
    }

    private ProceedingJoinPoint joinPointFor(String methodName, Class<?>[] parameterTypes,
                                             String[] parameterNames, Object[] arguments)
            throws NoSuchMethodException {
        Method method = SampleService.class.getMethod(methodName, parameterTypes);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(arguments);
        return joinPoint;
    }

    private Auditable auditable(AuditAction action, String entityType, String legacyIdExpression,
                                String entityIdExpression, String plantIdExpression,
                                AuditChangeMode changeMode) {
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(action);
        when(auditable.entityType()).thenReturn(entityType);
        when(auditable.entityIdExpression()).thenReturn(legacyIdExpression);
        when(auditable.entityId()).thenReturn(entityIdExpression);
        when(auditable.entityName()).thenReturn("");
        when(auditable.companyId()).thenReturn("");
        when(auditable.plantId()).thenReturn(plantIdExpression);
        when(auditable.warehouseId()).thenReturn("");
        when(auditable.reasonCode()).thenReturn("");
        when(auditable.changeMode()).thenReturn(changeMode);
        when(auditable.operation()).thenReturn(AuditOperation.INFERRED);
        return auditable;
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    void audit_success_recordsEntityNameChangesAndScopeThroughTheOutboxRecorder() throws Throwable {
        UUID uomId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        TestResponse response = new TestResponse(uomId, "Piece updated", plantId);
        Object[] arguments = {uomId, "Piece updated"};
        Map<String, JsonNode> before = Map.of();
        List<AuditFieldChange> changes = List.of(new AuditFieldChange(
                "name", "\"Piece\"", "\"Piece updated\"", AuditLogChangeType.UPDATE));

        ProceedingJoinPoint joinPoint = joinPointFor("update",
                new Class<?>[]{UUID.class, String.class}, new String[]{"uomId", "name"}, arguments);
        when(joinPoint.proceed()).thenReturn(response);
        Auditable auditable = auditable(AuditAction.UOM_UPDATED, "Uom", "uomId.toString()", "",
                "#result?.plantId()", AuditChangeMode.AUTO);
        when(captureService.captureBefore("Uom", arguments, AuditAction.UOM_UPDATED,
                AuditOperation.INFERRED)).thenReturn(before);
        when(captureService.resolveEntityName("Uom", before, response)).thenReturn("Piece updated");
        when(captureService.calculateChanges(AuditAction.UOM_UPDATED, AuditOperation.INFERRED,
                before, response)).thenReturn(changes);
        stubRecorderDraft();

        aspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(auditRecorder).recordSuccess(captor.capture());
        AuditRecordDraft draft = captor.getValue();
        assertThat(draft.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(draft.changes()).isEqualTo(changes);
        assertThat(draft.primaryEntity()).isNotNull().satisfies(entity -> {
            assertThat(entity.entityType()).isEqualTo("Uom");
            assertThat(entity.entityId()).isEqualTo(uomId.toString());
            assertThat(entity.entityName()).isEqualTo("Piece updated");
        });
        // audit_logs.plant_id was NULL on every row ever written before this: nothing populated it.
        assertThat(draft.scope().plantId()).isEqualTo(plantId);
        verify(auditRecorder, never()).recordFailure(any());
    }

    @Test
    void audit_voidMethod_takesTheEntityIdFromTheArgumentInsteadOfTheAbsentResult() throws Throwable {
        UUID workOrderId = UUID.randomUUID();
        Object[] arguments = {workOrderId};

        ProceedingJoinPoint joinPoint = joinPointFor("release",
                new Class<?>[]{UUID.class}, new String[]{"workOrderId"}, arguments);
        when(joinPoint.proceed()).thenReturn(null);
        // The legacy expression is the one ~120 call sites use; on a void method it evaluates against
        // nothing, which is why every void command used to record a null entity id.
        Auditable auditable = auditable(AuditAction.WORK_ORDER_RELEASED, "WorkOrder",
                "workOrderId.toString()", "#workOrderId", "", AuditChangeMode.NONE);
        stubRecorderDraft();

        aspect.audit(joinPoint, auditable);

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(auditRecorder).recordSuccess(captor.capture());
        assertThat(captor.getValue().primaryEntity())
                .extracting(AuditEntityRef::entityId)
                .isEqualTo(workOrderId.toString());
    }

    @Test
    void audit_failure_recordsThroughTheStandaloneWriterWithTheErrorCodeNotTheRawMessage()
            throws Throwable {
        UUID workOrderId = UUID.randomUUID();
        Object[] arguments = {workOrderId};

        ProceedingJoinPoint joinPoint = joinPointFor("release",
                new Class<?>[]{UUID.class}, new String[]{"workOrderId"}, arguments);
        when(joinPoint.proceed()).thenThrow(new AppException(
                ValidationErrorCode.RESOURCE_NOT_FOUND, "WorkOrder not found with id: " + workOrderId));
        Auditable auditable = auditable(AuditAction.WORK_ORDER_RELEASED, "WorkOrder", "",
                "#workOrderId", "", AuditChangeMode.NONE);
        stubRecorderDraft();

        assertThatThrownBy(() -> aspect.audit(joinPoint, auditable))
                .isInstanceOf(AppException.class);

        ArgumentCaptor<AuditRecordDraft> captor = ArgumentCaptor.forClass(AuditRecordDraft.class);
        verify(auditRecorder).recordFailure(captor.capture());
        AuditRecordDraft draft = captor.getValue();
        assertThat(draft.outcome()).isEqualTo(AuditOutcome.FAILURE);
        // Bound to the stable ErrorCode, not to the HTTP status and not to the message text.
        assertThat(draft.reasonCode()).isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND.code());
        assertThat(draft.primaryEntity().entityId()).isEqualTo(workOrderId.toString());
        verify(auditRecorder, never()).recordSuccess(any());
    }

    @Test
    void audit_recorderThrowing_neverChangesTheBusinessOutcome() throws Throwable {
        UUID uomId = UUID.randomUUID();
        Object[] arguments = {uomId, "Piece"};
        TestResponse response = new TestResponse(uomId, "Piece", null);

        ProceedingJoinPoint joinPoint = joinPointFor("update",
                new Class<?>[]{UUID.class, String.class}, new String[]{"uomId", "name"}, arguments);
        when(joinPoint.proceed()).thenReturn(response);
        Auditable auditable = auditable(AuditAction.UOM_UPDATED, "Uom", "", "#uomId", "",
                AuditChangeMode.NONE);
        when(auditRecorder.draft(any())).thenThrow(new IllegalStateException("audit backend down"));

        // FAIL_OPEN (decision §11.1): the command's own result reaches the caller untouched.
        assertThat(aspect.audit(joinPoint, auditable)).isSameAs(response);
    }
}
