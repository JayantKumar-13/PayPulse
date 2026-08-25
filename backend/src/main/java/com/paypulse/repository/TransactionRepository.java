package com.paypulse.repository;

import com.paypulse.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    List<TransactionEntity> findTop50ByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(String fromWalletId, String toWalletId);

    //Give me transactions where this wallet was either the sender OR the receiver.

    @Query("""
        select coalesce(sum(t.amount), 0)
        from TransactionEntity t
        where t.fromWalletId = :fromWalletId
          and t.status = 'success'
          and t.createdAt >= :since
        """)
    BigDecimal sumSuccessfulSentSince(@Param("fromWalletId") String fromWalletId, @Param("since") Instant since);
}
// coalesce - If the sum is NULL, return 0 instead.