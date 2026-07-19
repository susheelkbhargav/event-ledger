package com.eventledger.gateway.client;

import com.eventledger.gateway.client.dto.AccountDetailsResponse;
import com.eventledger.gateway.client.dto.ApplyTransactionRequest;
import com.eventledger.gateway.client.dto.ApplyTransactionResponse;
import com.eventledger.gateway.client.dto.BalanceResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AccountServiceCaller {

    private final RestClient restClient;

    public AccountServiceCaller(@Qualifier("accountServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "accountService")
    public ApplyTransactionResponse apply(ApplyTransactionRequest request) {
        return restClient.post().uri("/api/v1/transactions").body(request)
            .retrieve()
            .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                throw new AccountServiceServerException(res.getStatusCode().value());
            })
            .body(ApplyTransactionResponse.class);
    }

    @CircuitBreaker(name = "accountService")
    public BalanceResponse getBalance(String accountId) {
        return restClient.get().uri("/api/v1/accounts/{id}/balance", accountId)
            .retrieve()
            .onStatus(s -> s.value() == 404, (req, res) -> {
                throw new AccountNotFoundException(accountId);
            })
            .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                throw new AccountServiceServerException(res.getStatusCode().value());
            })
            .body(BalanceResponse.class);
    }

    @CircuitBreaker(name = "accountService")
    public AccountDetailsResponse getAccount(String accountId) {
        return restClient.get().uri("/api/v1/accounts/{id}", accountId)
            .retrieve()
            .onStatus(s -> s.value() == 404, (req, res) -> {
                throw new AccountNotFoundException(accountId);
            })
            .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                throw new AccountServiceServerException(res.getStatusCode().value());
            })
            .body(AccountDetailsResponse.class);
    }
}
