package com.eventledger.gateway.api;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.dto.AccountDetailsResponse;
import com.eventledger.gateway.client.dto.BalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class GatewayAccountController {

    private final AccountServiceClient accountServiceClient;

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return accountServiceClient.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountDetailsResponse details(@PathVariable String accountId) {
        return accountServiceClient.getAccount(accountId);
    }
}
