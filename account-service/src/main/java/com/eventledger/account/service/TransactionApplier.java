package com.eventledger.account.service;

import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.domain.AccountRepository;
import com.eventledger.account.domain.TransactionRecord;
import com.eventledger.account.domain.TransactionRecordRepository;
import com.eventledger.account.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionApplier {

    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void apply(ApplyTransactionRequest request) {
        transactionRecordRepository.saveAndFlush(TransactionRecord.from(request));
        BigDecimal delta = request.type() == TransactionType.CREDIT
            ? request.amount() : request.amount().negate();
        accountRepository.applyDelta(request.accountId(), delta);
    }
}
