package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.CheckInRequest;
import com.venuity.server.api.dto.CustomerCreateRequest;
import com.venuity.server.api.dto.CustomerResponse;
import com.venuity.server.api.dto.RechargeRequest;
import com.venuity.server.api.dto.RechargeResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Customer;
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.repo.CheckInRepository;
import com.venuity.server.repo.CustomerRepository;
import com.venuity.server.repo.WalletTransactionRepository;
import com.venuity.server.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class WalletFlowIntegrationTest {

    private static final String ADMIN_USERNAME = "wallet-flow-admin";
    private static final String ADMIN_PASSWORD = "WalletFlow#12345";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("venuity.bootstrap.admin-username", () -> ADMIN_USERNAME);
        registry.add("venuity.bootstrap.admin-password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private CustomerService customers;

    @Autowired
    private RechargeService recharge;

    @Autowired
    private WalletService wallet;

    @Autowired
    private CheckInService checkIns;

    @Autowired
    private SettingsService settings;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WalletTransactionRepository ledger;

    @Autowired
    private CheckInRepository checkInRepository;

    @Autowired
    private AppUserRepository users;

    @Test
    void sameIdempotencyKeyReturnsSameResponseAndCreatesOneLedgerRow() {
        CustomerResponse customer = createCustomer(BigDecimal.ZERO);
        String key = UUID.randomUUID().toString();
        RechargeRequest request = new RechargeRequest(new BigDecimal("100.00"), "idempotent top-up");

        RechargeResponse first = recharge.recharge(customer.publicId(), request, key, principal());
        RechargeResponse second = recharge.recharge(customer.publicId(), request, key, principal());

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(first.customer().publicId(), second.customer().publicId());
        assertEquals(first.customer().publicCode(), second.customer().publicCode());
        assertEquals(0, first.amount().compareTo(second.amount()));
        assertEquals(0, first.balanceAfter().compareTo(second.balanceAfter()));
        assertEquals(0, first.customer().walletBalance().compareTo(second.customer().walletBalance()));
        assertEquals(1, ledgerFor(customer).size());
        assertEquals(0, reload(customer).getWalletBalance().compareTo(new BigDecimal("100.00")));

        ApiException conflict = assertThrows(ApiException.class, () -> recharge.recharge(
                customer.publicId(), new RechargeRequest(new BigDecimal("50.00"), "different"), key,
                principal()));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.getCode());
        assertEquals(1, ledgerFor(customer).size());
        assertEquals(0, reload(customer).getWalletBalance().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void walletBalanceAlwaysEqualsLedgerSumAfterRandomOperations() {
        CustomerResponse customer = createCustomer(new BigDecimal("100.00"));
        Random random = new Random(42);
        for (int index = 0; index < 100; index++) {
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(5000) + 1, 2);
            if (random.nextBoolean()) {
                wallet.topUp(customerId(customer), amount, principal().id(), "random top-up");
            } else {
                try {
                    wallet.debit(customerId(customer), amount, TxType.CHECKIN, principal().id(), "random debit");
                } catch (ApiException exception) {
                    assertEquals(ErrorCode.INSUFFICIENT_BALANCE, exception.getCode());
                }
            }
        }

        for (Customer current : customerRepository.findAll()) {
            BigDecimal sum = ledger.sumByCustomerId(current.getId());
            assertEquals(0, current.getWalletBalance().compareTo(sum),
                    "balance diverged from ledger for customer " + current.getId());
            assertTrue(current.getWalletBalance().signum() >= 0,
                    "customer " + current.getId() + " has a negative balance");
        }
    }

    @Test
    void rejectsInvalidWalletOperations() {
        CustomerResponse customer = createCustomer(BigDecimal.TEN);
        long id = customerId(customer);
        AuthPrincipal principal = principal();

        assertEquals(ErrorCode.AMOUNT_OUT_OF_RANGE, assertThrows(ApiException.class,
                () -> wallet.topUp(id, new BigDecimal("0.001"), principal.id(), "too small")).getCode());
        assertEquals(ErrorCode.AMOUNT_OUT_OF_RANGE, assertThrows(ApiException.class,
                () -> wallet.topUp(id, new BigDecimal("100000000.00"), principal.id(), "too large")).getCode());
        assertEquals(ErrorCode.AMOUNT_OUT_OF_RANGE, assertThrows(ApiException.class,
                () -> wallet.debit(id, new BigDecimal("-1.00"), TxType.CHECKIN, principal.id(),
                        "negative")).getCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> wallet.topUp(Long.MAX_VALUE, BigDecimal.ONE, principal.id(), "missing")).getCode());

        CustomerResponse inactive = createCustomer(BigDecimal.TEN);
        customers.deactivate(inactive.publicId());
        assertEquals(ErrorCode.CUSTOMER_INACTIVE, assertThrows(ApiException.class,
                () -> wallet.debit(customerId(inactive), BigDecimal.ONE, TxType.CHECKIN,
                        principal.id(), "inactive")).getCode());
    }

    @Test
    void concurrentCheckInsSucceedOnlyWhileBalanceCoversFee() throws Exception {
        BigDecimal fee = settings.current().getCheckinFee();
        CustomerResponse customer = createCustomer(fee.multiply(BigDecimal.TEN));
        AuthPrincipal principal = principal();
        int attempts = 50;
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<? extends Future<?>> futures = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            checkIns.checkIn(new CheckInRequest(customer.publicCode()),
                                    UUID.randomUUID().toString(), principal);
                            successes.incrementAndGet();
                        } catch (ApiException exception) {
                            if (exception.getCode() == ErrorCode.INSUFFICIENT_BALANCE) {
                                insufficient.incrementAndGet();
                            } else {
                                unexpected.add(exception);
                            }
                        } catch (Throwable throwable) {
                            unexpected.add(throwable);
                        }
                    }))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(unexpected.isEmpty(), () -> "unexpected failures: " + unexpected);
        assertEquals(10, successes.get());
        assertEquals(40, insufficient.get());
        assertEquals(0, reload(customer).getWalletBalance().compareTo(BigDecimal.ZERO));
        assertEquals(10, checkInRepository.findAll().stream()
                .filter(checkIn -> checkIn.getCustomer().getId().equals(customerId(customer)))
                .count());
    }

    private CustomerResponse createCustomer(BigDecimal initialBalance) {
        return customers.modernCreate(new CustomerCreateRequest(
                "Wallet Flow " + UUID.randomUUID(),
                null,
                initialBalance,
                UUID.randomUUID().toString()), principal());
    }

    private long customerId(CustomerResponse customer) {
        return customerRepository.findByPublicIdIgnoreCase(customer.publicId()).orElseThrow().getId();
    }

    private Customer reload(CustomerResponse customer) {
        return customerRepository.findByPublicIdIgnoreCase(customer.publicId()).orElseThrow();
    }

    private List<WalletTransaction> ledgerFor(CustomerResponse customer) {
        long id = customerId(customer);
        return ledger.findAll().stream()
                .filter(transaction -> transaction.getCustomer().getId().equals(id))
                .toList();
    }

    private AuthPrincipal principal() {
        AppUser user = users.findByUsername(ADMIN_USERNAME).orElseThrow();
        return new AuthPrincipal(user.getId(), user.getPublicId(), user.getUsername(), user.getRole());
    }
}
