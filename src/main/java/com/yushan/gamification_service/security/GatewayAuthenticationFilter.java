package com.yushan.gamification_service.security;

import com.yushan.gamification_service.util.HmacUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gateway Authentication Filter
 * 
 * This filter trusts requests that have been validated by the API Gateway.
 * 
 * Flow:
 * 1. Check if request has X-Gateway-Validated header (gateway already validated)
 * 2. If yes, extract user info from gateway headers (X-User-Id, X-User-Email, X-User-Role)
 * 3. Create CustomUserDetails from headers
 * 4. Set authentication in SecurityContext
 * 
 * This filter replaces JWT validation - all JWT validation is done at Gateway level.
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Shared secret for HMAC signature verification
     * Must match the secret in API Gateway
     */
    @Value("${gateway.hmac.secret:${GATEWAY_HMAC_SECRET:yushan-gateway-hmac-secret-key-for-request-signature-2024}}")
    private String hmacSecret;

    /**
     * Filter method that processes each request
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain
     * @throws ServletException if servlet error occurs
     * @throws IOException if I/O error occurs
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Check if request is gateway-validated
            String gatewayValidated = request.getHeader("X-Gateway-Validated");
            
            if ("true".equals(gatewayValidated)) {
                // Extract user info from gateway headers
                String userId = request.getHeader("X-User-Id");
                String email = request.getHeader("X-User-Email");
                String username = request.getHeader("X-User-Username");
                String role = request.getHeader("X-User-Role");
                String statusStr = request.getHeader("X-User-Status");
                String timestampStr = request.getHeader("X-Gateway-Timestamp");
                String signature = request.getHeader("X-Gateway-Signature");
                
                // Security: Verify HMAC signature to prevent header forgery
                if (userId == null || email == null || timestampStr == null || signature == null) {
                    logger.warn("Gateway-validated request but missing required headers (userId, email, timestamp, or signature)");
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Invalid gateway headers\"}");
                    return;
                }
                
                try {
                    long timestamp = Long.parseLong(timestampStr);
                    
                    // Verify HMAC signature
                    if (!HmacUtil.verifySignature(userId, email, role, timestamp, signature, hmacSecret)) {
                        logger.warn("Gateway-validated request with invalid HMAC signature from IP: " + 
                                   request.getRemoteAddr() + " for path: " + request.getRequestURI());
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Invalid gateway signature\"}");
                        return;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid timestamp format in gateway headers: " + timestampStr);
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Invalid timestamp format\"}");
                    return;
                }
                
                // Signature verified - trust gateway headers
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Parse status from gateway headers (default to 0 = ACTIVE if not present)
                    Integer status = 0; // Default to NORMAL/ACTIVE
                    if (statusStr != null && !statusStr.isBlank()) {
                        try {
                            status = Integer.parseInt(statusStr);
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid status format in gateway headers: " + statusStr + ", defaulting to 0");
                            status = 0;
                        }
                    }
                    
                    // Create CustomUserDetails from gateway headers
                    CustomUserDetails userDetails = new CustomUserDetails(
                        userId, 
                        email, 
                        username, 
                        role != null ? role : "USER", 
                        status
                    );
                    
                    // Check if user is enabled (not suspended/banned) - same as JwtAuthenticationFilter
                    if (!userDetails.isEnabled()) {
                        // User is disabled, reject request with 403 Forbidden
                        logger.warn("Gateway-validated request but user is disabled (status: " + status + ") for user: " + userId + " from IP: " + request.getRemoteAddr() + " for path: " + request.getRequestURI());
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"User account is disabled or suspended\",\"status\":403}");
                        return;
                    }
                    
                    // Create authentication object
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()
                        );
                    
                    // Set additional details
                    authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    // Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    logger.debug("Gateway-validated request authenticated for user: " + email + " (" + userId + ")");
                }
            }
            // If not gateway-validated, JwtAuthenticationFilter will handle it (backward compatibility)
            
        } catch (Exception e) {
            // Log error but don't stop the request
            logger.error("Cannot set gateway authentication: " + e.getMessage(), e);
        }
        
        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Check if the request should be filtered
     * Skip filtering for certain paths (like health checks)
     * 
     * @param request HTTP request
     * @return true if should skip filtering, false otherwise
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip filtering for these paths
        return path.startsWith("/actuator/") ||
               path.equals("/error") ||
               // Skip OPTIONS requests (CORS preflight)
               "OPTIONS".equals(method);
    }
}

