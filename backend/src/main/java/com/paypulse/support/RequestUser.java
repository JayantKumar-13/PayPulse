package com.paypulse.support;

import com.paypulse.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class RequestUser {

    private RequestUser() {
    }

    public static String getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(RequestAttributes.USER_ID);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No token provided");
        }
        return userId.toString();
    }
}
