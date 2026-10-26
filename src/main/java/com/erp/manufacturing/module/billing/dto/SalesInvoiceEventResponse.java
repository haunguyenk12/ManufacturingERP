package com.erp.manufacturing.module.billing.dto;

import java.time.Instant;
import java.util.UUID;

/** One step of an invoice's life, read from the document itself: CREATED, POSTED, CANCELLED. */
public record SalesInvoiceEventResponse(String action, UUID actorId, String actorUsername, String reason, Instant at) {}
