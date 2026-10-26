package com.erp.manufacturing.module.delivery.dto;

import java.time.Instant;
import java.util.UUID;

/** One step of a delivery's life, read from the document itself: CREATED, POSTED, CANCELLED. */
public record DeliveryEventResponse(String action, UUID actorId, String actorUsername, String reason, Instant at) {}
