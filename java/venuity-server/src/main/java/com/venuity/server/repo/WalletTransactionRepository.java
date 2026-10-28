package com.venuity.server.repo;

import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long>,
        JpaSpecificationExecutor<WalletTransaction> {

    List<WalletTransaction> findTop10ByOrderByCreatedAtDescIdDesc();

    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.type = :type")
    BigDecimal sumByType(@Param("type") TxType type);

    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t
            where t.type = :type and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumByTypeBetween(@Param("type") TxType type,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    long countByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(TxType type, Instant from, Instant to);

    @Query("""
            select t from WalletTransaction t
            join fetch t.customer
            where t.createdAt >= :from and t.createdAt < :to and t.type in :types
            order by t.createdAt
            """)
    List<WalletTransaction> findForAnalytics(@Param("from") Instant from,
                                             @Param("to") Instant to,
                                             @Param("types") Collection<TxType> types);

    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.customer.id = :customerId")
    BigDecimal sumByCustomerId(@Param("customerId") long customerId);
}
