package com.paypulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    // record -: Java automatically generates:constructor , getters (email(), password()),equals(),hashCode(),toString()

    public record SignupRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 32) String password,
        @NotBlank @Size(min = 3, max = 20) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,        //only numbers , _ and chars.
        @NotBlank @Pattern(regexp = "^\\d{6}$") String pin,     //Exactly 6 digits
        @NotBlank @Size(min = 6, max = 6) String otp
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 32) String password
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 32) String newPassword
    ) {
    }

    public record ChangePinRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$") String oldPin,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String newPin
    ) {
    }

    public record SendOtpRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record EmailRequest(
        @NotBlank @Email String email
    ) {
    }

    public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp,
        @NotBlank @Size(min = 8, max = 32) String newPassword
    ) {
    }

    public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp
    ) {
    }

    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record UserView(String id, String name, String username, String role) {
    }

    public record AuthResponse(String msg, String accessToken, String refreshToken, UserView user) {
    }

    public record RefreshResponse(String accessToken) {
    }
}
