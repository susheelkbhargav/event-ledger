package com.eventledger.gateway.domain;

import com.eventledger.gateway.api.dto.EventRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "event_record",
       indexes = @Index(name = "idx_event_account_ts", columnList = "accountId, eventTimestamp"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRecord implements Persistable<String> {

    @Id
    @Column(updatable = false, nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Lob
    private String metadata;

    @Transient
    private boolean newRecord = true;

    @PostPersist
    @PostLoad
    void markNotNew() { this.newRecord = false; }

    @Override public String getId() { return eventId; }
    @Override public boolean isNew() { return newRecord; }

    public static EventRecord from(EventRequest request) {
        EventRecord r = new EventRecord();
        r.eventId = request.eventId();
        r.accountId = request.accountId();
        r.type = request.type();
        r.amount = request.amount();
        r.currency = request.currency();
        r.eventTimestamp = request.eventTimestamp();
        r.status = EventStatus.PENDING;
        r.metadata = request.metadata();
        return r;
    }

    public void markApplied() { this.status = EventStatus.APPLIED; }
    public void markQueued() { this.status = EventStatus.QUEUED; }
}
