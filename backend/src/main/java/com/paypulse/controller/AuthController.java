package com.paypulse.controller;

import com.paypulse.dto.AuthDtos;
import com.paypulse.dto.CommonDtos;
import com.paypulse.service.AuthService;
import com.paypulse.service.RateLimiterService;
import com.paypulse.support.RequestUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/signup")
    public AuthDtos.AuthResponse signup(@Valid @RequestBody AuthDtos.SignupRequest request,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse response) {
        applyRateLimit("rl:signup", getClientIp(httpRequest), 15 * 60 * 1000L, 15,
            "Too many signup attempts, please try again after 15 minutes.", response);   //15 attempts per 15 minutes per IP.
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse response) {
        applyRateLimit("rl:login", getClientIp(httpRequest), 15 * 60 * 1000L, 20,
            "Too many login attempts, please try again after 15 minutes.", response); //20 attempts per 15 minutes per IP.
        //This protects against password brute-force attacks.
        return authService.login(request);
    }

    @PatchMapping("/change-password")
    public CommonDtos.MessageResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,
                                                     HttpServletRequest httpRequest) {
        return authService.changePassword(RequestUser.getUserId(httpRequest), request);
    }

    @PatchMapping("/change-pin")
    public CommonDtos.MessageResponse changePin(@Valid @RequestBody AuthDtos.ChangePinRequest request,
                                                HttpServletRequest httpRequest) {
        return authService.changePin(RequestUser.getUserId(httpRequest), request);
    }

    @PostMapping("/send-otp")
    public CommonDtos.MessageResponse sendOtp(@Valid @RequestBody AuthDtos.SendOtpRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse response) {
        applyRateLimit("rl:otp", getClientIp(httpRequest), 60_000L, 5,
            "Too many OTP requests. Please wait 1 minute.", response);
        return authService.sendOtp(request);
    }

    @PostMapping("/send-signup-otp")
    public CommonDtos.MessageResponse sendSignupOtp(@Valid @RequestBody AuthDtos.EmailRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse response) {
        applyRateLimit("rl:otp", getClientIp(httpRequest), 60_000L, 5,
            "Too many OTP requests. Please wait 1 minute.", response);
        return authService.sendSignupOtp(request);
    }

    @PostMapping("/forgot-password")
    public CommonDtos.MessageResponse forgotPassword(@Valid @RequestBody AuthDtos.EmailRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse response) {
        applyRateLimit("rl:otp", getClientIp(httpRequest), 60_000L, 5,
            "Too many OTP requests. Please wait 1 minute.", response);
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public CommonDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse response) {
        applyRateLimit("rl:verify-otp", getClientIp(httpRequest), 15 * 60 * 1000L, 15,
            "Too many OTP verification attempts.", response);
        return authService.resetPassword(request);
    }

    @PostMapping("/verify-otp")
    public AuthDtos.AuthResponse verifyOtp(@Valid @RequestBody AuthDtos.VerifyOtpRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse response) {
        applyRateLimit("rl:verify-otp", getClientIp(httpRequest), 15 * 60 * 1000L, 15,
            "Too many OTP verification attempts.", response);
        return authService.verifyOtp(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.RefreshResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public CommonDtos.MessageResponse logout(@RequestBody(required = false) AuthDtos.LogoutRequest request) {
        return authService.logout(request == null ? new AuthDtos.LogoutRequest(null) : request);
    }

    private void applyRateLimit(String keyPrefix,
                                String identity,
                                long windowMs,
                                int max,
                                String message,
                                HttpServletResponse response) {
        RateLimiterService.Decision decision = rateLimiterService.check(keyPrefix, identity, windowMs, max);

        //Returns something like: record Decision( boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds )

        response.setHeader("X-RateLimit-Limit", String.valueOf(max));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            throw new com.paypulse.exception.ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    //If you use only getRemoteAddr(), all users may appear as the proxy IP, and your rate limiter becomes almost useless.
    //Use this
//    private String getClientIp(HttpServletRequest request) {
//        String ip = request.getHeader("X-Forwarded-For");
//        if (ip != null && !ip.isBlank()) {
//            return ip.split(",")[0].trim();  X-Forwarded-For may contain multiple IPs.The first IP is usually the real client IP.
//        }
//        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
//    }
}
