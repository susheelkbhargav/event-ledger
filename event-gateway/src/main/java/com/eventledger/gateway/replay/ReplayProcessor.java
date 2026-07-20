package com.eventledger.gateway.replay;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.dto.ApplyTransactionRequest;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.service.EventPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Non-transactional: the remote call must not hold a DB transaction open
// (spec §9a) — the transition inside EventPersistenceService is the
// per-event transaction.
@Component
@RequiredArgsConstructor
public class ReplayProcessor {

    private final AccountServiceClient accountServiceClient;
    private final EventPersistenceService eventPersistenceService;

    public void processOne(EventRecord record) {
        accountServiceClient.apply(new ApplyTransactionRequest(record.getEventId(), record.getAccountId(),
            record.getType(), record.getAmount(), record.getCurrency(), record.getEventTimestamp()));
        eventPersistenceService.transition(record.getEventId(), EventStatus.APPLIED, false);
    }
}
