package com.paypulse.service;

import java.math.BigDecimal;
import com.paypulse.config.AppProperties;
import com.paypulse.dto.AuthDtos;
import com.paypulse.dto.CommonDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.model.RefreshTokenEntity;
import com.paypulse.model.UserEntity;
import com.paypulse.model.WalletEntity;
import com.paypulse.repository.RefreshTokenRepository;
import com.paypulse.repository.UserRepository;
import com.paypulse.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final MailService mailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AppProperties properties;

    public AuthService(UserRepository userRepository,
                       WalletRepository walletRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       TotpService totpService,
                       MailService mailService,
                       AppProperties properties) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.mailService = mailService;
        this.properties = properties;
    }

    @Transactional
    public AuthDtos.AuthResponse signup(AuthDtos.SignupRequest request) {
        String email = normalizeEmail(request.email());             //This prevents duplicate accounts caused by different casing/spaces.
        String username = normalizeUsername(request.username());
        if (!totpService.verifyOtp(email, request.otp())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wrong or expired OTP");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email Already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());           //Generates a unique user ID.
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setHashedPin(passwordEncoder.encode(request.pin()));
        userRepository.save(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(UUID.randomUUID().toString());
        wallet.setUserId(user.getId());
        wallet.setBalance(BigDecimal.valueOf(properties.getInitialWalletBalance()));
        wallet.setQrCode(buildQrDataUrl(username));
        walletRepository.save(wallet);

        TokenPair tokenPair = generateTokenPair(user.getId());
        return new AuthDtos.AuthResponse(
            "Signup Successful with signup bonus of ₹5000",
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            new AuthDtos.UserView(user.getId(), user.getName(), user.getUsername(), null)
        );
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User does not exist"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong password");
        }

        TokenPair tokenPair = generateTokenPair(user.getId());
        return new AuthDtos.AuthResponse(
            "Login successful",
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            new AuthDtos.UserView(user.getId(), user.getName(), user.getUsername(), user.getRole())
        );
    }

    @Transactional
    public CommonDtos.MessageResponse changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong Password Entered");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
        return new CommonDtos.MessageResponse("Password changed successfully");
    }

    @Transactional
    public CommonDtos.MessageResponse changePin(String userId, AuthDtos.ChangePinRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User Not Found"));
        if (!passwordEncoder.matches(request.oldPin(), user.getHashedPin())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect PIN entered");
        }
        user.setHashedPin(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);
        return new CommonDtos.MessageResponse("Pin changed successfully");
    }

    public CommonDtos.MessageResponse sendSignupOtp(AuthDtos.EmailRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        mailService.sendOtpEmail(email, totpService.generateOtp(email));
        return new CommonDtos.MessageResponse("OTP sent to your email for signup");
    }

    public CommonDtos.MessageResponse sendOtp(AuthDtos.SendOtpRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong password");
        }
        mailService.sendOtpEmail(user.getEmail(), totpService.generateOtp(user.getEmail()));
        return new CommonDtos.MessageResponse("OTP sent to your email");
    }

    public CommonDtos.MessageResponse forgotPassword(AuthDtos.EmailRequest request) {
        Optional<UserEntity> user = userRepository.findByEmail(normalizeEmail(request.email()));
        user.ifPresent(found -> mailService.sendOtpEmail(found.getEmail(), totpService.generateOtp(found.getEmail())));
        return new CommonDtos.MessageResponse("If an account exists for this email, a password reset OTP has been sent.");
    }

    @Transactional
    public CommonDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        if (!totpService.verifyOtp(email, request.otp())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wrong or expired OTP");
        }
        UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        return new CommonDtos.MessageResponse("Password reset successfully. Please log in with your new password.");
    }

    public AuthDtos.AuthResponse verifyOtp(AuthDtos.VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        if (!totpService.verifyOtp(email, request.otp())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wrong or expired OTP");
        }
        UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        TokenPair tokenPair = generateTokenPair(user.getId());
        return new AuthDtos.AuthResponse(
            "Login successful",
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            new AuthDtos.UserView(user.getId(), user.getName(), user.getUsername(), user.getRole())
        );
    }

    public AuthDtos.RefreshResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshTokenEntity token = refreshTokenRepository.findById(request.refreshToken())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (token.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        return new AuthDtos.RefreshResponse(jwtService.generateAccessToken(token.getUserId()));
    }

    @Transactional
    public CommonDtos.MessageResponse logout(AuthDtos.LogoutRequest request) {
        if (request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenRepository.revokeByToken(request.refreshToken());
        }
        return new CommonDtos.MessageResponse("Logged out successfully");
    }

    private TokenPair generateTokenPair(String userId) {
        String refreshTokenValue = jwtService.generateRefreshTokenValue();
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUserId(userId);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60));
        refreshTokenRepository.save(refreshToken);
        return new TokenPair(jwtService.generateAccessToken(userId), refreshTokenValue);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private String buildQrDataUrl(String username) {
        String payload = "paypulse://pay?to=" + username;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"280\" height=\"280\" viewBox=\"0 0 280 280\">"
            + "<rect width=\"280\" height=\"280\" fill=\"white\"/>"
            + "<rect x=\"20\" y=\"20\" width=\"240\" height=\"240\" rx=\"16\" fill=\"#111827\"/>"
            + "<text x=\"140\" y=\"120\" font-family=\"Arial\" font-size=\"22\" text-anchor=\"middle\" fill=\"white\">PayPulse</text>"
            + "<text x=\"140\" y=\"155\" font-family=\"Arial\" font-size=\"12\" text-anchor=\"middle\" fill=\"#d1d5db\">"
            + payload
            + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }
}
