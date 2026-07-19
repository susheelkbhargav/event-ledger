package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventRequest;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventPersistenceService {

    private final EventRecordRepository eventRecordRepository;

    @Transactional
    public EventRecord insert(EventRequest request) {
        return eventRecordRepository.saveAndFlush(EventRecord.from(request));
    }

    @Transactional
    public EventRecord transition(String eventId, EventStatus status) {
        EventRecord record = eventRecordRepository.findById(eventId).orElseThrow();
        switch (status) {
            case APPLIED -> record.markApplied();
            case QUEUED -> record.markQueued();
            case PENDING -> throw new IllegalArgumentException("Cannot transition back to PENDING");
        }
        return record;
    }
}
