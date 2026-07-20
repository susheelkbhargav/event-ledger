package com.eventledger.gateway.replay;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplayScheduler {

    private final EventRecordRepository eventRecordRepository;
    private final ReplayProcessor replayProcessor;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${gateway.replay.batch-size}")
    private int batchSize;

    // Skips while the circuit is OPEN (spec §7); a per-event failure leaves
    // the row QUEUED for the next tick.
    @Scheduled(fixedDelayString = "${gateway.replay.interval}")
    public void replayQueued() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("accountService");
        if (breaker.getState() == CircuitBreaker.State.OPEN) return;

        List<EventRecord> queued = eventRecordRepository
            .findByStatusOrderByReceivedAtAsc(EventStatus.QUEUED, PageRequest.of(0, batchSize));
        for (EventRecord record : queued) {
            try {
                replayProcessor.processOne(record);
            } catch (Exception e) {
                log.warn("Replay failed for event {}, will retry next tick", record.getEventId(), e);
            }
        }
    }
}
