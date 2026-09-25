package com.erp.manufacturing.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field / accessor / record component whose value must never reach the audit trail (AR-1).
 *
 * <p>Before this annotation existed the only protection was a name-fragment denylist
 * ({@code password}, {@code token}, …) inside {@code AuditChangeCaptureService}. A denylist alone is
 * a guess: it drops innocent fields whose name happens to contain a fragment, and — the dangerous
 * direction — it lets a secret through the moment somebody names it {@code recoveryPhrase} or
 * {@code apiKey}. The declaration is the authoritative statement; the fragment list stays on as a
 * safety net for code nobody has annotated yet.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditSensitive {
}
