package com.erp.manufacturing.common.audit.spi;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a descriptor or change provider can see about one audited call (AR-4).
 *
 * @param method          the audited method, for {@code supports(...)} checks and error messages
 * @param arguments       positional arguments as passed
 * @param argumentNames   parameter names, so a provider can look one up without counting positions
 * @param result          the return value; {@code null} for {@code void} or on the failure path
 * @param failure         the thrown exception, or {@code null} on the success path
 * @param auditable       the annotation that triggered the interception
 * @param beforeSnapshot  committed entity state captured before the call; empty when not applicable
 * @param preState        whatever {@code AuditDescriptorProvider.capturePreState} recorded before the
 *                        call ran; {@code null} when no provider claimed the invocation. This is what
 *                        lets a provider tell "the grant was created" from "the grant was already
 *                        there and the command did nothing" — a distinction that is impossible to
 *                        recover afterwards, because both end states are identical
 */
public record AuditInvocation(
        Method method,
        Object[] arguments,
        String[] argumentNames,
        Object result,
        Throwable failure,
        Auditable auditable,
        Map<String, JsonNode> beforeSnapshot,
        Object preState
) {

    /** Convenience for the pre-call phase, where there is no result and no pre-state yet. */
    public static AuditInvocation before(Method method, Object[] arguments, String[] argumentNames,
                                         Auditable auditable, Map<String, JsonNode> beforeSnapshot) {
        return new AuditInvocation(method, arguments, argumentNames, null, null, auditable,
                beforeSnapshot, null);
    }

    public AuditInvocation withOutcome(Object result, Throwable failure, Object preState) {
        return new AuditInvocation(method, arguments, argumentNames, result, failure, auditable,
                beforeSnapshot, preState);
    }

    /** The pre-state cast to the type the provider stored, or empty if absent / of another type. */
    public <T> Optional<T> preState(Class<T> type) {
        return type.isInstance(preState) ? Optional.of(type.cast(preState)) : Optional.empty();
    }

    public AuditAction action() {
        return auditable.action();
    }

    public String entityType() {
        return auditable.entityType() == null || auditable.entityType().isBlank()
                ? null : auditable.entityType();
    }

    public boolean failed() {
        return failure != null;
    }

    /** Looks an argument up by parameter name, so a provider never has to hard-code a position. */
    public Optional<Object> argument(String name) {
        if (argumentNames == null || arguments == null) {
            return Optional.empty();
        }
        for (int i = 0; i < argumentNames.length && i < arguments.length; i++) {
            if (name.equals(argumentNames[i])) {
                return Optional.ofNullable(arguments[i]);
            }
        }
        return Optional.empty();
    }

    /** First argument assignable to {@code type}; the common case for a single-id command. */
    public <T> Optional<T> argumentOfType(Class<T> type) {
        if (arguments == null) {
            return Optional.empty();
        }
        for (Object argument : arguments) {
            if (type.isInstance(argument)) {
                return Optional.of(type.cast(argument));
            }
        }
        return Optional.empty();
    }
}
