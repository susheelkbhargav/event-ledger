package com.eventledger.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, UUID> {

    long countByAccountId(String accountId);
}
