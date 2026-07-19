package com.eventledger.account.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account implements Persistable<String> {

    @Id
    @Column(updatable = false, nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Transient
    private boolean newRecord = true;

    @PostPersist
    @PostLoad
    void markNotNew() { this.newRecord = false; }

    @Override public String getId() { return accountId; }
    @Override public boolean isNew() { return newRecord; }

    public static Account open(String accountId) {
        Account account = new Account();
        account.accountId = accountId;
        account.balance = BigDecimal.ZERO;
        return account;
    }
}
