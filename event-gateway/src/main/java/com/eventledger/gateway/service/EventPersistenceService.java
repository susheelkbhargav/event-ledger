package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventRequest;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventPersistenceService {

    private final EventRecordRepository eventRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EventRecord insert(EventRequest request) {
        return eventRecordRepository.saveAndFlush(EventRecord.from(request));
    }

    // countReceived: the submit path counts its outcome here (published
    // inside the TX, counted after commit); replay passes false — its
    // receipt was already counted as "queued".
    @Transactional
    public EventRecord transition(String eventId, EventStatus status, boolean countReceived) {
        EventRecord record = eventRecordRepository.findById(eventId).orElseThrow();
        switch (status) {
            case APPLIED -> record.markApplied();
            case QUEUED -> record.markQueued();
            case PENDING -> throw new IllegalArgumentException("Cannot transition back to PENDING");
        }
        if (countReceived) {
            eventPublisher.publishEvent(new EventOutcomeEvent(record.getType(),
                status == EventStatus.APPLIED ? "applied" : "queued"));
        }
        return record;
    }
}
