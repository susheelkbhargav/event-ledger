package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.TransactionType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// applied/queued are published inside the transition TX and counted only
// AFTER_COMMIT (spec §9a) — a rolled-back transition must not increment.
// duplicate/rejected are counted inline: no committed write to drift from.
@Component
@RequiredArgsConstructor
public class EventMetrics {

    private final MeterRegistry meterRegistry;

    public void countReceived(TransactionType type, String outcome) {
        meterRegistry.counter("gateway.events.received",
            "type", type == null ? "UNKNOWN" : type.name(), "outcome", outcome).increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutcome(EventOutcomeEvent event) {
        countReceived(event.type(), event.outcome());
    }
}
