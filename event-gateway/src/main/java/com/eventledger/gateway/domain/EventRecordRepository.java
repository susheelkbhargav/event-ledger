package com.eventledger.gateway.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRecordRepository extends JpaRepository<EventRecord, String> {

    List<EventRecord> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId, Pageable pageable);

    List<EventRecord> findByStatusOrderByReceivedAtAsc(EventStatus status, Pageable pageable);
}
