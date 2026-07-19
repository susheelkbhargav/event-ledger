package com.eventledger.gateway.client.dto;

public record ApplyTransactionResponse(String eventId, String accountId, boolean duplicate) {}
