package com.venuity.server.repo;

import com.venuity.server.domain.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPublicIdIgnoreCase(String publicId);

    Optional<Customer> findByPublicCodeIgnoreCase(String publicCode);

    boolean existsByPublicCodeIgnoreCase(String publicCode);

    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            select c from Customer c
            where (:active is null or c.active = :active)
              and (:query is null
                   or lower(c.name) like lower(concat('%', :query, '%'))
                   or lower(coalesce(c.phone, '')) like lower(concat('%', :query, '%'))
                   or lower(c.publicCode) like lower(concat('%', :query, '%')))
            """)
    Page<Customer> search(@Param("query") String query, @Param("active") Boolean active, Pageable pageable);

    @Query("""
            select c from Customer c
            where replace(lower(c.publicCode), '-', '') = replace(lower(:code), '-', '')
            """)
    Optional<Customer> findByNormalizedCode(@Param("code") String code);

    @Query("""
            select c.id from Customer c
            where replace(lower(c.publicCode), '-', '') = replace(lower(:code), '-', '')
            """)
    Optional<Long> findIdByNormalizedCode(@Param("code") String code);

    @Query("select c.id from Customer c where lower(c.publicId) = lower(:publicId)")
    Optional<Long> findIdByPublicIdIgnoreCase(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") long id);

    long countByActiveTrue();
}
