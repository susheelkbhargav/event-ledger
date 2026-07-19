package com.eventledger.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface AccountRepository extends JpaRepository<Account, String> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance + :delta WHERE a.accountId = :accountId")
    int applyDelta(@Param("accountId") String accountId, @Param("delta") BigDecimal delta);
}
