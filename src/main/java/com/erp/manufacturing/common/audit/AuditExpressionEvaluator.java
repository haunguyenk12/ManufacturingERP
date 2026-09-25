package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses, caches and evaluates the SpEL on {@link Auditable} (AR-4).
 *
 * <p>Three deliberate differences from the previous inline parser:
 *
 * <ol>
 *   <li><strong>Arguments are visible.</strong> The old evaluator used the return value as the root
 *       object and nothing else, so a {@code void} method — {@code release(UUID workOrderId)},
 *       {@code delete(UUID id)} — had no way to name what it acted on and recorded a null entity id.</li>
 *   <li><strong>Expressions are parsed once per expression, not once per call.</strong> Parsing SpEL
 *       on every audited invocation is pure waste on a hot path.</li>
 *   <li><strong>A broken expression is loud.</strong> A parse failure throws, because it is a coding
 *       error that is identical on every call and should surface the first time the method runs — the
 *       old code logged it at DEBUG and returned {@code null}, so a typo silently emptied a column
 *       forever. An <em>evaluation</em> failure is different and stays quiet: {@code #result?.plantId}
 *       legitimately yields nothing when the result is null, and that must not break the request.</li>
 * </ol>
 */
@Component
@Slf4j
public class AuditExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /** Evaluates against the full invocation context; {@code null} when the expression is blank. */
    public <T> T evaluate(String expression, AuditInvocation invocation, Class<T> expectedType) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Expression parsed = cache.computeIfAbsent(expression, this::parse);
        try {
            return parsed.getValue(contextFor(invocation), expectedType);
        } catch (Exception e) {
            // Evaluation-time misses are normal (a null result, an absent optional field).
            log.debug("[Audit] Expression '{}' on {} yielded nothing: {}",
                    expression, invocation.method().getName(), e.getMessage());
            return null;
        }
    }

    public String evaluateString(String expression, AuditInvocation invocation) {
        Object value = evaluate(expression, invocation, Object.class);
        return value == null ? null : value.toString();
    }

    /** Accepts either a {@code UUID} or its text form, since either is natural at a call site. */
    public UUID evaluateUuid(String expression, AuditInvocation invocation) {
        Object value = evaluate(expression, invocation, Object.class);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            log.warn("[Audit] Expression '{}' on {} produced '{}', which is not a UUID",
                    expression, invocation.method().getName(), value);
            return null;
        }
    }

    /**
     * <strong>Legacy form.</strong> Root object is the return value, no variables. Retained for the
     * {@code entityIdExpression} attribute that ~120 existing call sites still use.
     */
    public String evaluateOnResult(String expression, Object result) {
        if (expression == null || expression.isBlank() || result == null) {
            return null;
        }
        try {
            return cache.computeIfAbsent(expression, this::parse)
                    .getValue(new StandardEvaluationContext(result), String.class);
        } catch (Exception e) {
            log.debug("[Audit] Legacy expression '{}' yielded nothing: {}", expression, e.getMessage());
            return null;
        }
    }

    private Expression parse(String expression) {
        try {
            return parser.parseExpression(expression);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unparseable @Auditable SpEL expression: " + expression, e);
        }
    }

    private StandardEvaluationContext contextFor(AuditInvocation invocation) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", invocation.result());
        context.setVariable("exception", invocation.failure());
        context.setVariable("args", invocation.arguments());
        String[] names = invocation.argumentNames();
        Object[] arguments = invocation.arguments();
        if (names != null && arguments != null) {
            for (int i = 0; i < names.length && i < arguments.length; i++) {
                if (names[i] != null) {
                    context.setVariable(names[i], arguments[i]);
                }
            }
        }
        return context;
    }
}
