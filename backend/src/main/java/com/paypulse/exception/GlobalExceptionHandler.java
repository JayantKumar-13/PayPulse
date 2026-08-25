package com.paypulse.exception;

import com.paypulse.dto.CommonDtos;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonDtos.MessageResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new CommonDtos.MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonDtos.MessageResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new CommonDtos.MessageResponse(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonDtos.MessageResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new CommonDtos.MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<CommonDtos.MessageResponse> handleJwt(JwtException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new CommonDtos.MessageResponse("Invalid token"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonDtos.MessageResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new CommonDtos.MessageResponse("An unexpected error occurred. Please try again."));
    }
}
