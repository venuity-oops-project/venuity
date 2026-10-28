package com.venuity.server.web;

import com.venuity.server.api.dto.CheckInRequest;
import com.venuity.server.api.dto.CheckInResponse;
import com.venuity.server.security.SecurityUtils;
import com.venuity.server.service.CheckInService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkins")
public class CheckInController {

    private final CheckInService checkIns;

    public CheckInController(CheckInService checkIns) {
        this.checkIns = checkIns;
    }

    @PostMapping
    public CheckInResponse checkIn(@Valid @RequestBody CheckInRequest request,
                                   @RequestHeader("Idempotency-Key") String idemKey) {
        return checkIns.checkIn(request, idemKey, SecurityUtils.principal());
    }
}
