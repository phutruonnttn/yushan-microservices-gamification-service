package com.yushan.gamification_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Feign Client Authentication Configuration
 * 
 * This interceptor forwards authentication headers for inter-service calls.
 * 
 * Priority:
 * 1. If request has X-Gateway-Validated header (from API Gateway), forward gateway headers including HMAC signature
 * 2. Otherwise, forward Authorization header (JWT token) for backward compatibility
 */
@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (!(requestAttributes instanceof ServletRequestAttributes)) {
                return;
            }

            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

            // Check if request is gateway-validated
            String gatewayValidated = request.getHeader("X-Gateway-Validated");
            
            if ("true".equals(gatewayValidated)) {
                // Forward gateway headers including HMAC signature (preferred - no JWT validation needed at target service)
                String userId = request.getHeader("X-User-Id");
                String email = request.getHeader("X-User-Email");
                String username = request.getHeader("X-User-Username");
                String role = request.getHeader("X-User-Role");
                String status = request.getHeader("X-User-Status");
                String timestamp = request.getHeader("X-Gateway-Timestamp");
                String signature = request.getHeader("X-Gateway-Signature");
                
                if (userId != null) {
                    template.header("X-Gateway-Validated", "true");
                    template.header("X-User-Id", userId);
                    if (email != null) {
                        template.header("X-User-Email", email);
                    }
                    if (username != null) {
                        template.header("X-User-Username", username);
                    }
                    if (role != null) {
                        template.header("X-User-Role", role);
                    }
                    if (status != null) {
                        template.header("X-User-Status", status);
                    }
                    // Forward HMAC signature headers for verification
                    if (timestamp != null) {
                        template.header("X-Gateway-Timestamp", timestamp);
                    }
                    if (signature != null) {
                        template.header("X-Gateway-Signature", signature);
                    }
                }
            } else {
                // Fallback: forward JWT token (for backward compatibility or direct calls)
                String authorization = request.getHeader("Authorization");
                if (authorization != null && !authorization.isBlank()) {
                    template.header("Authorization", authorization);
                }
            }
        };
    }
}
