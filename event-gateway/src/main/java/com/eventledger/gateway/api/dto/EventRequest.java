package com.eventledger.gateway.api.dto;

import com.eventledger.gateway.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record EventRequest(
    @NotBlank String eventId,
    @NotBlank String accountId,
    @NotNull TransactionType type,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String currency,
    @NotNull Instant eventTimestamp,
    String metadata
) {}
