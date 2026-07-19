package com.eventledger.account.service;

import com.eventledger.account.api.dto.AccountDetailsResponse;
import com.eventledger.account.api.dto.BalanceResponse;
import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountRepository;
import com.eventledger.account.domain.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    public BalanceResponse balance(String accountId) {
        Account account = load(accountId);
        return new BalanceResponse(account.getAccountId(), account.getBalance());
    }

    public AccountDetailsResponse details(String accountId) {
        Account account = load(accountId);
        return new AccountDetailsResponse(account.getAccountId(), account.getBalance(),
            transactionRecordRepository.countByAccountId(accountId));
    }

    private Account load(String accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
