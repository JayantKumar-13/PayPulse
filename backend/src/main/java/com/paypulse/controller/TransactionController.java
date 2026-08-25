package com.paypulse.controller;

import com.paypulse.dto.CommonDtos;
import com.paypulse.dto.TransactionDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.service.IdempotencyService;
import com.paypulse.service.RateLimiterService;
import com.paypulse.service.TransferService;
import com.paypulse.support.RequestUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction")
public class TransactionController {

    private final TransferService transferService;
    private final RateLimiterService rateLimiterService;
    private final IdempotencyService idempotencyService;

    public TransactionController(TransferService transferService,
                                 RateLimiterService rateLimiterService,
                                 IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.rateLimiterService = rateLimiterService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransactionDtos.TransferRequest request,@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,HttpServletRequest httpRequest,HttpServletResponse response) {

        //If required = true(Idem Key), Spring would only catch missing, not blank.

        String userId = RequestUser.getUserId(httpRequest);
        applyRateLimit(userId, response);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        String scopeKey = "idem:" + userId + ":POST:/api/transaction/transfer:" + idempotencyKey;
        //scope key is the unique identifier used by the IdempotencyService to represent one logical transfer request.
        //If both users send same Idem key , they can cause collision . So we use Scope Key.
        //Why include the endpoint?
        //Imagine the client reuses the same idempotency key for another API They are different operations.
        //Including the endpoint prevents accidental replay across APIs.
        // I have Also included the HTTP method , because different methods could mean different things.

        //Why not let the client send the full key? I can but This prevents clients from interfering with other users or endpoints.

        IdempotencyService.ClaimResult claimResult = idempotencyService.claim(scopeKey);
        if (claimResult.state() == IdempotencyService.ClaimState.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "A request with this Idempotency-Key is already being processed");
        }
        if (claimResult.state() == IdempotencyService.ClaimState.COMPLETED) {
            return ResponseEntity.status(claimResult.storedResponse().statusCode()).body(claimResult.storedResponse().body());
        }

        try {
            ResponseEntity<CommonDtos.MessageResponse> entity = ResponseEntity.ok(
                transferService.transferMoney(userId, request, idempotencyKey)
            );
            idempotencyService.complete(scopeKey, entity);
            return entity;
        } catch (RuntimeException ex) {
            idempotencyService.release(scopeKey);
            throw ex;
        }
    }

    private void applyRateLimit(String userId, HttpServletResponse response) {
        RateLimiterService.Decision decision = rateLimiterService.check("rl:transfer", userId, 10_000L, 30);
        response.setHeader("X-RateLimit-Limit", "30");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many transfer attempts. Please wait 10 seconds and try again.");
        }
    }
}
