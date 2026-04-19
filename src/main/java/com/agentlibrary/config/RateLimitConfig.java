package com.agentlibrary.config;

import com.agentlibrary.auth.LoginRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limiting configuration.
 * Creates the {@link RateLimitFilter} bean (not auto-registered via @Component
 * to avoid double-registration when added explicitly to the security filter chain).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(LoginRateLimiter loginRateLimiter) {
        return new RateLimitFilter(loginRateLimiter);
    }
}

