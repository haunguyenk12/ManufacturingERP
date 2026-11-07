package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.module.auth.domain.PasswordPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Sends account emails through {@link JavaMailSender} when {@code spring.mail.host} is configured
 * (QA points it at an isolated mailbox such as Mailpit); otherwise it only logs that a message was
 * queued. Either way a secret — reset token or one-time code — never reaches a log line.
 *
 * <p>Password-code emails are {@code @Async} and swallow their own failures: an SMTP outage must
 * not fail the login or the request that triggered the send, and the user can ask for another code.
 * "Sent" therefore means <em>handed to the mail server</em>, not delivered.
 *
 * <p>Single concrete class on purpose — no interface until a second mailer exists
 * (coding-rules.md §11.5).
 */
@Service
@Slf4j
public class EmailNotificationService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSender,
                                    @Value("${app.mail.from:no-reply@omniplant.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendPasswordResetEmail(String email, String resetToken) {
        // resetToken is a bearer credential and must never enter application/provider logs.
        log.info("[EMAIL-MOCK] Password reset notification queued for {} (token redacted)", email);
    }

    /** The 6-digit code for a SETUP or RESET challenge. */
    @Async
    public void sendPasswordCode(String to, PasswordPurpose purpose, String code, Instant expiresAt) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("[EMAIL] No mail server configured (spring.mail.host); {} code for {} NOT sent (code redacted)",
                    purpose, to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(purpose == PasswordPurpose.SETUP
                    ? "Your OmniPlant account setup code" : "Your OmniPlant password reset code");
            message.setText("Your verification code is " + code + ".\n\nIt expires at " + expiresAt
                    + " (UTC). If you did not request it, ignore this email.");
            sender.send(message);
            log.info("[EMAIL] {} code handed to the mail server for {}", purpose, to);
        } catch (RuntimeException e) {
            log.error("[EMAIL] Failed to send {} code to {}: {}", purpose, to, e.getClass().getSimpleName());
        }
    }
}
