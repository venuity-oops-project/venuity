package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.CustomerCreateRequest;
import com.venuity.server.api.dto.CustomerResponse;
import com.venuity.server.api.dto.CustomerUpdateRequest;
import com.venuity.server.api.dto.LegacyCustomerCreateRequest;
import com.venuity.server.api.dto.LegacyCustomerResponse;
import com.venuity.server.api.dto.LegacyPageResponse;
import com.venuity.server.api.dto.PageResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.Customer;
import com.venuity.server.repo.CustomerRepository;
import com.venuity.server.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final WalletService wallet;
    private final SettingsService settings;

    public CustomerService(CustomerRepository customers, WalletService wallet, SettingsService settings) {
        this.customers = customers;
        this.wallet = wallet;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LegacyPageResponse<LegacyCustomerResponse> legacyList(String search, int page, int pageSize,
                                                                 HttpServletRequest request) {
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        int safePage = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> result = search == null || search.isBlank()
                ? customers.findAll(pageable)
                : customers.findByNameContainingIgnoreCase(search.trim(), pageable);
        ZoneId zone = settings.zone();
        List<LegacyCustomerResponse> rows = result.getContent().stream()
                .map(customer -> legacy(customer, zone))
                .toList();
        return legacyPage(result, rows, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> modernList(String query, int page, int size, Boolean active) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> result = customers.search(
                query == null || query.isBlank() ? null : query.trim(), active, pageable);
        return PageResponse.of(result.map(this::modern));
    }

    @Transactional
    public LegacyCustomerResponse legacyCreate(LegacyCustomerCreateRequest request, AuthPrincipal principal) {
        String name = validName(request.name());
        BigDecimal initial = parseInitial(request.initialBalance());
        String code = request.qrId() == null || request.qrId().isBlank()
                ? UUID.randomUUID().toString()
                : request.qrId().trim();
        Customer customer = create(name, null, code, principal);
        if (initial.signum() > 0) {
            wallet.topUp(customer.getId(), initial, principal.id(),
                    "Initial balance loaded at registration.");
        }
        return legacy(customer, settings.zone());
    }

    @Transactional
    public CustomerResponse modernCreate(CustomerCreateRequest request, AuthPrincipal principal) {
        String name = validName(request.name());
        BigDecimal initial = MoneyUtil.nonNegative(request.initialBalance());
        String code = request.publicCode() == null || request.publicCode().isBlank()
                ? UUID.randomUUID().toString()
                : request.publicCode().trim();
        Customer customer = create(name, request.phone(), code, principal);
        if (initial.signum() > 0) {
            wallet.topUp(customer.getId(), initial, principal.id(),
                    "Initial balance loaded at registration.");
        }
        return modern(customer);
    }

    @Transactional(readOnly = true)
    public LegacyCustomerResponse legacyByPublicId(String publicId) {
        return legacy(requireByPublicId(publicId), settings.zone());
    }

    @Transactional(readOnly = true)
    public CustomerResponse modernByPublicId(String publicId) {
        return modern(requireByPublicId(publicId));
    }

    @Transactional(readOnly = true)
    public LegacyCustomerResponse legacyByCode(String code) {
        return legacy(requireByCode(code), settings.zone());
    }

    @Transactional(readOnly = true)
    public CustomerResponse modernByCode(String code) {
        return modern(requireByCode(code));
    }

    @Transactional
    public CustomerResponse modernUpdate(String publicId, CustomerUpdateRequest request) {
        Customer customer = requireByPublicId(publicId);
        if (request.name() != null) {
            customer.setName(validName(request.name()));
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        return modern(customers.save(customer));
    }

    @Transactional
    public void deactivate(String publicId) {
        Customer customer = requireByPublicId(publicId);
        customer.setActive(false);
        customers.save(customer);
    }

    @Transactional
    public CustomerResponse reactivate(String publicId) {
        Customer customer = requireByPublicId(publicId);
        customer.setActive(true);
        return modern(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public long requireIdByPublicId(String publicId) {
        return customers.findIdByPublicIdIgnoreCase(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer not found")
                        .withLegacyError("No Customer matches the given query."));
    }

    @Transactional(readOnly = true)
    public long requireIdByPublicIdForCheckIn(String publicId) {
        return customers.findIdByPublicIdIgnoreCase(publicId)
                .orElseThrow(() -> checkInNotFound(publicId));
    }

    @Transactional(readOnly = true)
    public long requireIdByCodeForCheckIn(String code) {
        if (code == null || code.isBlank()) {
            throw invalidQr("missing");
        }
        String trimmed = code.trim();
        return customers.findIdByNormalizedCode(trimmed)
                .or(() -> customers.findIdByPublicIdIgnoreCase(trimmed))
                .orElseThrow(() -> checkInNotFound(trimmed));
    }

    @Transactional(readOnly = true)
    public Customer requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw invalidQr("missing");
        }
        String trimmed = code.trim();
        return customers.findByNormalizedCode(trimmed)
                .or(() -> customers.findByPublicIdIgnoreCase(trimmed))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer not found")
                        .withLegacyError("Customer not found for identifier: " + trimmed));
    }

    private ApiException checkInNotFound(String identifier) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Customer not found")
                .withLegacyError("Customer not found for identifier: " + identifier)
                .withoutLegacyCode();
    }

    public LegacyCustomerResponse legacy(Customer customer, ZoneId zone) {
        return new LegacyCustomerResponse(customer.getPublicId(), customer.getName(),
                MoneyUtil.plain(customer.getWalletBalance()), customer.getPublicCode(),
                TimeUtil.legacy(customer.getCreatedAt(), zone));
    }

    public CustomerResponse modern(Customer customer) {
        return new CustomerResponse(customer.getPublicId(), customer.getPublicCode(), customer.getName(),
                customer.getPhone(), MoneyUtil.normalize(customer.getWalletBalance()), customer.isActive(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }

    private Customer create(String name, String phone, String publicCode, AuthPrincipal principal) {
        if (customers.existsByPublicCodeIgnoreCase(publicCode)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "QR code is already assigned")
                    .withLegacyError("Validation failed.")
                    .withLegacyDetail(Map.of("qr_id", List.of("This QR code is already assigned.")));
        }
        Customer customer = Customer.create(name, phone == null || phone.isBlank() ? null : phone.trim(),
                publicCode);
        return customers.saveAndFlush(customer);
    }

    private Customer requireByPublicId(String publicId) {
        return customers.findByPublicIdIgnoreCase(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer not found")
                        .withLegacyError("No Customer matches the given query."));
    }

    private String validName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Validation failed")
                    .withLegacyDetail(Map.of("name", List.of("This field may not be blank.")));
        }
        String trimmed = name.trim();
        if (trimmed.length() > 150) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Validation failed")
                    .withLegacyDetail(Map.of("name", List.of("Ensure this field has no more than 150 characters.")));
        }
        return trimmed;
    }

    private BigDecimal parseInitial(String initial) {
        if (initial == null || initial.isBlank()) {
            return MoneyUtil.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(initial.trim());
            BigDecimal normalized = MoneyUtil.normalize(value);
            if (normalized.signum() < 0) {
                throw new NumberFormatException("negative");
            }
            if (normalized.compareTo(MoneyUtil.MAX_AMOUNT) > 0) {
                throw new NumberFormatException("too large");
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Validation failed")
                    .withLegacyDetail(Map.of("initial_balance",
                            List.of("Ensure this value is greater than or equal to 0.")));
        }
    }

    private ApiException invalidQr(String code) {
        return new ApiException(ErrorCode.INVALID_QR, HttpStatus.BAD_REQUEST,
                "Invalid QR code")
                .withLegacyError("Customer not found for identifier: " + code);
    }

    private LegacyPageResponse<LegacyCustomerResponse> legacyPage(Page<Customer> page,
                                                                  List<LegacyCustomerResponse> rows,
                                                                  HttpServletRequest request) {
        int totalPages = Math.max(1, (int) Math.ceil(page.getTotalElements() / (double) page.getSize()));
        int number = page.getNumber();
        String next = (number + 1) < totalPages
                ? ServletUriComponentsBuilder.fromRequest(request)
                .replaceQueryParam("page", number + 2)
                .build()
                .toUriString()
                : null;
        String previous = number > 0
                ? ServletUriComponentsBuilder.fromRequest(request)
                .replaceQueryParam("page", number)
                .build()
                .toUriString()
                : null;
        return new LegacyPageResponse<>(page.getTotalElements(), totalPages, next, previous, rows);
    }
}
