package com.agentlibrary.config;

import com.agentlibrary.auth.LoginRateLimiter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Authentication success handler that resets the rate limit bucket for the
 * client IP upon successful login, then delegates to the default redirect behaviour.
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginRateLimiter loginRateLimiter;

    public LoginSuccessHandler(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String clientIp = RateLimitFilter.extractClientIp(request);
        loginRateLimiter.resetBucket(clientIp);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
