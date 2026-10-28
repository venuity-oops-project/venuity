package com.venuity.server.web;

import com.venuity.server.api.dto.CustomerCreateRequest;
import com.venuity.server.api.dto.CustomerResponse;
import com.venuity.server.api.dto.CustomerUpdateRequest;
import com.venuity.server.api.dto.PageResponse;
import com.venuity.server.api.dto.RechargeRequest;
import com.venuity.server.api.dto.RechargeResponse;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.SecurityUtils;
import com.venuity.server.service.CustomerService;
import com.venuity.server.service.QrService;
import com.venuity.server.service.RechargeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;
    private final RechargeService recharge;
    private final QrService qr;

    public CustomerController(CustomerService customers, RechargeService recharge, QrService qr) {
        this.customers = customers;
        this.recharge = recharge;
        this.qr = qr;
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active) {
        return customers.modernList(query, page, size, active);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        AuthPrincipal principal = SecurityUtils.principal();
        return ResponseEntity.status(HttpStatus.CREATED).body(customers.modernCreate(request, principal));
    }

    @GetMapping("/{publicId}")
    public CustomerResponse detail(@PathVariable String publicId) {
        return customers.modernByPublicId(publicId);
    }

    @PutMapping("/{publicId}")
    public CustomerResponse update(@PathVariable String publicId,
                                   @Valid @RequestBody CustomerUpdateRequest request) {
        return customers.modernUpdate(publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String publicId) {
        customers.deactivate(publicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse reactivate(@PathVariable String publicId) {
        return customers.reactivate(publicId);
    }

    @GetMapping("/by-code/{code}")
    public CustomerResponse byCode(@PathVariable String code) {
        return customers.modernByCode(code);
    }

    @GetMapping(value = "/{publicId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String publicId,
                                     @RequestParam(defaultValue = "512") int size) {
        CustomerResponse customer = customers.modernByPublicId(publicId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + publicId + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(qr.png(customer.publicCode(), size));
    }

    @PostMapping("/{publicId}/recharge")
    public RechargeResponse recharge(@PathVariable String publicId,
                                     @Valid @RequestBody RechargeRequest request,
                                     @RequestHeader("Idempotency-Key") String idemKey) {
        return recharge.recharge(publicId, request, idemKey, SecurityUtils.principal());
    }
}
