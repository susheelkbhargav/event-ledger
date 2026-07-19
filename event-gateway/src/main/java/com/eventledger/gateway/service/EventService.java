package com.eventledger.gateway.service;

import com.eventledger.gateway.api.dto.EventRequest;
import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.dto.ApplyTransactionRequest;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRecordRepository eventRecordRepository;
    private final EventPersistenceService eventPersistenceService;
    private final AccountServiceClient accountServiceClient;

    // NO @Transactional here — the catch must sit outside the failed
    // transaction (spec §9a), and the remote call must not hold a DB
    // transaction open.
    public SubmissionResult submit(EventRequest request) {
        EventRecord saved;
        try {
            saved = eventPersistenceService.insert(request);
        } catch (DataIntegrityViolationException e) {
            EventRecord existing = eventRecordRepository.findById(request.eventId()).orElseThrow();
            return new SubmissionResult(existing, SubmissionResult.Outcome.DUPLICATE);
        }
        log.info("event {} accepted for account {}", saved.getEventId(), saved.getAccountId());
        accountServiceClient.apply(new ApplyTransactionRequest(saved.getEventId(), saved.getAccountId(),
            saved.getType(), saved.getAmount(), saved.getCurrency(), saved.getEventTimestamp()));
        EventRecord applied = eventPersistenceService.transition(saved.getEventId(), EventStatus.APPLIED);
        return new SubmissionResult(applied, SubmissionResult.Outcome.CREATED);
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
