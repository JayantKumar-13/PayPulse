package com.paypulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransferRequest(
        @NotBlank @Size(min = 3, max = 20) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String receiverUsername,
        @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal amount,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String pin,         //This is the transaction PIN, not the login password.
        @Size(max = 100) String note

        // @Min is for integers not for decimals.
    ) {
    }
}
//Why use username instead of userId? B/C users remember name instead of id , and it is more user-friendly.

//Why no sender field? B/C the sender is taken from the authenticated JWT.

