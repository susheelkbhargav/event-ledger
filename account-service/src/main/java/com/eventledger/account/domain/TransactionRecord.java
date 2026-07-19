package com.eventledger.account.domain;

import com.eventledger.account.api.dto.ApplyTransactionRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_record",
    uniqueConstraints = @UniqueConstraint(name = "uk_tx_event_id", columnNames = "eventId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
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
    private Instant appliedAt;

    public static TransactionRecord from(ApplyTransactionRequest request) {
        TransactionRecord r = new TransactionRecord();
        r.eventId = request.eventId();
        r.accountId = request.accountId();
        r.type = request.type();
        r.amount = request.amount();
        r.currency = request.currency();
        r.eventTimestamp = request.eventTimestamp();
        return r;
    }
}
