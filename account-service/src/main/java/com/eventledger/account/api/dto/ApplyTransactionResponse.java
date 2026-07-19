package com.eventledger.account.api.dto;

public record ApplyTransactionResponse(String eventId, String accountId, boolean duplicate) {}
