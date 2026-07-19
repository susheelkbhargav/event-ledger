package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.EventRecord;

public record SubmissionResult(EventRecord record, Outcome outcome) {
    public enum Outcome { CREATED, DUPLICATE, QUEUED }
}
