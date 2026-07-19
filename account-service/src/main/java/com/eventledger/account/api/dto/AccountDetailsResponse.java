package com.eventledger.account.api.dto;

import java.math.BigDecimal;

public record AccountDetailsResponse(String accountId, BigDecimal balance, long transactionCount) {}
