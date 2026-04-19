package com.agentlibrary.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter for login endpoint.
 * Returns 429 Too Many Requests when rate limit is exceeded.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Bucket loginRateLimitBucket;

    public RateLimitFilter(Bucket loginRateLimitBucket) {
        this.loginRateLimitBucket = loginRateLimitBucket;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getRequestURI())) {
            if (!loginRateLimitBucket.tryConsume(1)) {
                response.setStatus(429);
                response.getWriter().write("Too Many Requests");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
