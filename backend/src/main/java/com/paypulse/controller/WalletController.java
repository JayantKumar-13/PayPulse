package com.paypulse.controller;

import com.paypulse.dto.PaymentDtos;
import com.paypulse.dto.WalletDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.service.IdempotencyService;
import com.paypulse.service.PaymentService;
import com.paypulse.service.WalletService;
import com.paypulse.support.RequestUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public WalletController(WalletService walletService,
                            PaymentService paymentService,
                            IdempotencyService idempotencyService) {
        this.walletService = walletService;
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping({"", "/", "/me", "/balance"})
    public WalletDtos.WalletResponse getWallet(HttpServletRequest request) {
        return walletService.getMyWallet(RequestUser.getUserId(request));
    }

    @GetMapping("/transactions")
    public List<WalletDtos.TransactionResponse> getTransactions(HttpServletRequest request) {
        return walletService.getMyTransactions(RequestUser.getUserId(request));
    }

    @PostMapping("/topup")
    public ResponseEntity<?> topup(@Valid @RequestBody PaymentDtos.TopupRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   HttpServletRequest httpRequest) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        String userId = RequestUser.getUserId(httpRequest);
        String scopeKey = "idem:" + userId + ":POST:/api/wallet/topup:" + idempotencyKey;
        IdempotencyService.ClaimResult claimResult = idempotencyService.claim(scopeKey);
        if (claimResult.state() == IdempotencyService.ClaimState.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "A request with this Idempotency-Key is already being processed");
        }
        if (claimResult.state() == IdempotencyService.ClaimState.COMPLETED) {
            return ResponseEntity.status(claimResult.storedResponse().statusCode()).body(claimResult.storedResponse().body());
        }

        try {
            ResponseEntity<PaymentDtos.TopupResponse> response = ResponseEntity.accepted()
                .body(paymentService.initiateTopup(userId, request.amount()));
            idempotencyService.complete(scopeKey, response);
            return response;
        } catch (RuntimeException ex) {
            idempotencyService.release(scopeKey);
            throw ex;
        }
    }

    @GetMapping("/topup/{paymentId}")
    public PaymentDtos.PaymentStatusResponse getTopupStatus(@PathVariable String paymentId, HttpServletRequest request) {
        return paymentService.getTopupStatus(RequestUser.getUserId(request), paymentId);
    }
}
