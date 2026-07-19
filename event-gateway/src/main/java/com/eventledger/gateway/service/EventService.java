package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventRequest;
import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRecordRepository eventRecordRepository;
    private final EventPersistenceService eventPersistenceService;

    // NO @Transactional here — the catch must sit outside the failed
    // transaction (spec §9a), and the remote call added in Task 5 must not
    // hold a DB transaction open.
    public SubmissionResult submit(EventRequest request) {
        try {
            EventRecord saved = eventPersistenceService.insert(request);
            return new SubmissionResult(saved, SubmissionResult.Outcome.CREATED);
        } catch (DataIntegrityViolationException e) {
            EventRecord existing = eventRecordRepository.findById(request.eventId()).orElseThrow();
            return new SubmissionResult(existing, SubmissionResult.Outcome.DUPLICATE);
        }
    }

    @Transactional(readOnly = true)
    public EventResponse findById(String eventId) {
        return eventRecordRepository.findById(eventId).map(EventResponse::from)
            .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> findByAccount(String accountId, Pageable pageable) {
        return eventRecordRepository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId, pageable)
            .stream().map(EventResponse::from).toList();
    }
}
