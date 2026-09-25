package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AuditExpressionEvaluator")
class AuditExpressionEvaluatorTest {

    private final AuditExpressionEvaluator evaluator = new AuditExpressionEvaluator();

    @SuppressWarnings("unused")
    static class SampleService {
        public void release(UUID workOrderId, String reason) {
        }
    }

    private record SampleResponse(UUID workOrderId, String workOrderNo, UUID plantId) {
    }

    private AuditInvocation invocation(Object[] arguments, Object result) throws Exception {
        Method method = SampleService.class.getMethod("release", UUID.class, String.class);
        return new AuditInvocation(method, arguments, new String[]{"workOrderId", "reason"},
                result, null, mock(Auditable.class), Map.of(), null);
    }

    @Test
    @DisplayName("reads a named method argument, which is what makes void methods auditable")
    void namedArgument_isVisibleToTheExpression() throws Exception {
        UUID workOrderId = UUID.randomUUID();

        // The previous evaluator used the return value as the root and nothing else, so every void
        // command recorded a null entity id — it had no way to name what it acted on.
        String result = evaluator.evaluateString("#workOrderId",
                invocation(new Object[]{workOrderId, "damaged"}, null));

        assertThat(result).isEqualTo(workOrderId.toString());
    }

    @Test
    @DisplayName("reads the result, and tolerates a null result via safe navigation")
    void resultExpression_isSafeWhenTheResultIsNull() throws Exception {
        UUID plantId = UUID.randomUUID();

        assertThat(evaluator.evaluateUuid("#result?.plantId()",
                invocation(new Object[]{UUID.randomUUID(), "x"},
                        new SampleResponse(UUID.randomUUID(), "WO-1", plantId))))
                .isEqualTo(plantId);

        assertThat(evaluator.evaluateUuid("#result?.plantId()",
                invocation(new Object[]{UUID.randomUUID(), "x"}, null)))
                .isNull();
    }

    @Test
    @DisplayName("accepts a UUID given as text as well as a UUID instance")
    void uuidExpression_acceptsBothForms() throws Exception {
        UUID id = UUID.randomUUID();

        assertThat(evaluator.evaluateUuid("#workOrderId", invocation(new Object[]{id, "x"}, null)))
                .isEqualTo(id);
        assertThat(evaluator.evaluateUuid("#reason", invocation(new Object[]{id, id.toString()}, null)))
                .isEqualTo(id);
    }

    @Test
    @DisplayName("a value that is not a UUID yields null instead of throwing at the call site")
    void uuidExpression_nonUuidYieldsNull() throws Exception {
        assertThat(evaluator.evaluateUuid("#reason",
                invocation(new Object[]{UUID.randomUUID(), "not-a-uuid"}, null)))
                .isNull();
    }

    @Test
    @DisplayName("an unparseable expression fails loudly, because it is broken on every call")
    void malformedExpression_throws() throws Exception {
        // A parse error is a coding mistake identical on every invocation, so it must surface the
        // first time the method runs. The old evaluator logged it at DEBUG and returned null, which
        // is how a typo could empty a column forever without anyone noticing.
        assertThatThrownBy(() -> evaluator.evaluateString("#workOrderId.((",
                invocation(new Object[]{UUID.randomUUID(), "x"}, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unparseable");
    }

    @Test
    @DisplayName("a blank expression is simply absent, not an error")
    void blankExpression_returnsNull() throws Exception {
        assertThat(evaluator.evaluateString("", invocation(new Object[]{}, null))).isNull();
        assertThat(evaluator.evaluateString(null, invocation(new Object[]{}, null))).isNull();
    }

    @Test
    @DisplayName("the legacy result-rooted form still works for the call sites that use it")
    void legacyExpression_stillEvaluatesAgainstTheResult() {
        UUID workOrderId = UUID.randomUUID();

        assertThat(evaluator.evaluateOnResult("workOrderId.toString()",
                new SampleResponse(workOrderId, "WO-1", null)))
                .isEqualTo(workOrderId.toString());
        // …and returns null on a void method, which is exactly the defect entityId() replaces.
        assertThat(evaluator.evaluateOnResult("workOrderId.toString()", null)).isNull();
    }
}
