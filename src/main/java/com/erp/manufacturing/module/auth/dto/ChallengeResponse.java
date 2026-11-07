package com.erp.manufacturing.module.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** A code was issued: when it stops working and when another one may be requested. */
public record ChallengeResponse(UUID id, Instant expiresAt, Instant resendAt) {}
