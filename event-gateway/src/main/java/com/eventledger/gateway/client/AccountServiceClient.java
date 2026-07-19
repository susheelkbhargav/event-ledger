package com.eventledger.gateway.client;

import com.eventledger.gateway.client.dto.AccountDetailsResponse;
import com.eventledger.gateway.client.dto.ApplyTransactionRequest;
import com.eventledger.gateway.client.dto.ApplyTransactionResponse;
import com.eventledger.gateway.client.dto.BalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

@Service
@RequiredArgsConstructor
public class AccountServiceClient {

    private final AccountServiceCaller caller;

    @Retryable(includes = {ResourceAccessException.class, AccountServiceServerException.class},
               maxRetries = 2, delay = 200, multiplier = 2, jitter = 100)
    public ApplyTransactionResponse apply(ApplyTransactionRequest request) {
        return caller.apply(request);
    }

    @Retryable(includes = {ResourceAccessException.class, AccountServiceServerException.class},
               maxRetries = 2, delay = 200, multiplier = 2, jitter = 100)
    public BalanceResponse getBalance(String accountId) {
        return caller.getBalance(accountId);
    }

    @Retryable(includes = {ResourceAccessException.class, AccountServiceServerException.class},
               maxRetries = 2, delay = 200, multiplier = 2, jitter = 100)
    public AccountDetailsResponse getAccount(String accountId) {
        return caller.getAccount(accountId);
    }
}
