package com.yushan.gamification_service.config;

import com.yushan.gamification_service.security.CustomMethodSecurityExpressionHandler;
import com.yushan.gamification_service.security.GatewayAuthenticationFilter;
import com.yushan.gamification_service.security.JwtAuthenticationEntryPoint;
import com.yushan.gamification_service.security.JwtAuthenticationFilter;
import com.yushan.gamification_service.security.UserActivityFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration for Gamification Service.
 */
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private UserActivityFilter userActivityFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                
                // Disable CORS - handled by API Gateway
                .cors(cors -> cors.disable())
                
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints - no authentication required
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()

                        // Swagger/OpenAPI endpoints - MUST COME FIRST
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()

                        // Test endpoints (development only)
                        .requestMatchers("/api/test/**").permitAll()

                        // Static resources
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/static/**").permitAll()

                        // CORS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers( "/api/v1/gamification/stats/all").permitAll()
                        .requestMatchers("/api/v1/gamification/stats/batch").permitAll()

                        // Admin gamification endpoints - require ADMIN role
                        .requestMatchers("/api/v1/gamification/admin/**").hasRole("ADMIN")

                        // Gamification APIs - require authentication
                        .requestMatchers(HttpMethod.GET, "/api/v1/gamification/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/gamification/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Add Gateway filter first (for gateway-validated requests)
                // Then add JWT filter (for backward compatibility with direct service calls or inter-service calls)
                // JWT validation is primarily handled at Gateway level, but services can still validate JWT for backward compatibility
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userActivityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new CustomMethodSecurityExpressionHandler();
    }
}