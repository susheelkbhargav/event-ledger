package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountCreator {

    private final AccountRepository accountRepository;

    @Transactional
    public void insert(String accountId) {
        accountRepository.saveAndFlush(Account.open(accountId));
    }
}
