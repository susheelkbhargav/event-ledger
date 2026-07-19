package com.eventledger.gateway.client.dto;

import java.math.BigDecimal;

public record BalanceResponse(String accountId, BigDecimal balance) {}
