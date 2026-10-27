package com.erp.manufacturing.module.billing.dto;

import java.time.Instant;
import java.util.UUID;

/** One step of a payment's life, read from the document itself: CREATED, POSTED, CANCELLED. */
public record CustomerPaymentEventResponse(String action, UUID actorId, String actorUsername, String reason, Instant at) {}
