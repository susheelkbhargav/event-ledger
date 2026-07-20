package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.TransactionType;

public record EventOutcomeEvent(TransactionType type, String outcome) {}
