package com.eventledger.gateway.client.dto;

import java.math.BigDecimal;

public record AccountDetailsResponse(String accountId, BigDecimal balance, long transactionCount) {}
