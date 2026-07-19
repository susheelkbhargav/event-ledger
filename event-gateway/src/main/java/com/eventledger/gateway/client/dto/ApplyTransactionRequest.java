package com.eventledger.gateway.client.dto;

import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplyTransactionRequest(
    String eventId,
    String accountId,
    TransactionType type,
    BigDecimal amount,
    String currency,
    Instant eventTimestamp) {}
