package com.agentlibrary.config;

import com.agentlibrary.auth.LoginRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter for the login endpoint.
 * Applies per-IP token bucket rate limiting via {@link LoginRateLimiter}.
 * Returns 429 Too Many Requests when the rate limit is exceeded.
 *
 * <p>Registered explicitly in SecurityConfig filter chain (before
 * UsernamePasswordAuthenticationFilter), not via {@code @Component}
 * to avoid double-registration.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter loginRateLimiter;

    public RateLimitFilter(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getRequestURI())) {
            String clientIp = extractClientIp(request);
            if (!loginRateLimiter.resolveBucket(clientIp).tryConsume(1)) {
                response.setStatus(429);
                response.getWriter().write("Too Many Requests");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts client IP, checking X-Forwarded-For header first (for reverse proxy deployments).
     */
    static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP (original client) from comma-separated list
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

