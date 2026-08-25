package com.paypulse.filter;

import com.paypulse.service.JwtService;
import com.paypulse.support.RequestAttributes;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    //OncePerRequestFilter - Guarantees the filter runs only once per HTTP request.

    private static final Set<String> EXACT_PROTECTED_PATHS = Set.of(
        "/api/auth/change-password",
        "/api/auth/change-pin",
        "/api/auth/logout"
    );

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (EXACT_PROTECTED_PATHS.contains(path)) {
            return false;                           // These paths must get filtered
        }
        return !(path.startsWith("/api/wallet")
            || path.startsWith("/api/transaction"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"msg\":\"No token provided\"}");
            return;
        }

        try {
            String userId = jwtService.parseAccessToken(authHeader.substring(7));
            request.setAttribute(RequestAttributes.USER_ID, userId);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"msg\":\"Token expired, please login again\"}");
        } catch (JwtException ex) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "{\"msg\":\"Invalid token\"}");
        }
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }
}
