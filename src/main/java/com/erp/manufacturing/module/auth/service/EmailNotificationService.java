package com.erp.manufacturing.module.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock email sender (D8c) — logs the message instead of sending a real email. No real SMTP/API
 * integration exists in this project yet (decision: mock/log console). Kept as a single concrete
 * class with no interface — an abstraction with one implementation is speculative until a real
 * mailer is actually decided (coding-rules.md §11.5).
 */
@Service
@Slf4j
public class EmailNotificationService {

    public void sendPasswordResetEmail(String email, String resetToken) {
        // resetToken is a bearer credential and must never enter application/provider logs.
        log.info("[EMAIL-MOCK] Password reset notification queued for {} (token redacted)", email);
    }
}
