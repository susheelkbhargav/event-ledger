package com.eventledger.account.service;

import com.eventledger.account.api.dto.ApplyTransactionRequest;
import com.eventledger.account.api.dto.ApplyTransactionResponse;
import com.eventledger.account.domain.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionApplier transactionApplier;
    private final AccountCreator accountCreator;
    private final AccountRepository accountRepository;

    public ApplyTransactionResponse apply(ApplyTransactionRequest request) {
        ensureAccountExists(request.accountId());
        try {
            transactionApplier.apply(request);
            return new ApplyTransactionResponse(request.eventId(), request.accountId(), false);
        } catch (DataIntegrityViolationException e) {
            return new ApplyTransactionResponse(request.eventId(), request.accountId(), true);
        }
    }

    private void ensureAccountExists(String accountId) {
        if (accountRepository.existsById(accountId)) return;
        try {
            accountCreator.insert(accountId);
        } catch (DataIntegrityViolationException e) {
            // concurrent creation won the race — account exists, proceed
        }
    }
}
