package com.eventledger.gateway.api.dto;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record EventResponse(
    String eventId,
    String accountId,
    TransactionType type,
    BigDecimal amount,
    String currency,
    Instant eventTimestamp,
    Instant receivedAt,
    EventStatus status,
    String metadata
) {
    public static EventResponse from(EventRecord r) {
        return new EventResponse(r.getEventId(), r.getAccountId(), r.getType(), r.getAmount(),
            r.getCurrency(), r.getEventTimestamp(), r.getReceivedAt(), r.getStatus(), r.getMetadata());
    }
}
