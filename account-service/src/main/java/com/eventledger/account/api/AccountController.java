package com.eventledger.account.api;

import com.eventledger.account.api.dto.AccountDetailsResponse;
import com.eventledger.account.api.dto.BalanceResponse;
import com.eventledger.account.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountQueryService accountQueryService;

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return accountQueryService.balance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountDetailsResponse details(@PathVariable String accountId) {
        return accountQueryService.details(accountId);
    }
}
