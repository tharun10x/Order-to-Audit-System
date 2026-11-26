package com.ordertoaudit.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

/**
 * The one security setup, used by every profile (R2.1, R2.6). Here: who is logged in. Which role may call
 * which endpoint is declared on each controller method with @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String LOGIN_PATH = "/api/auth/login";

    @Bean
    SecurityFilterChain security(HttpSecurity http, JsonSecurityErrors errors) throws Exception {
        http
                // No cookies and no session: the browser never sends credentials by itself, so CSRF cannot happen
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring's internal forward to /error after a failure. Without this a 500 would show up
                        // as 401. Calling /error directly is an ordinary request and still needs a token
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .bearerTokenResolver(tokenResolver())
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }

    /** The claim "role": "ADMIN" becomes the authority ROLE_ADMIN, which hasRole('ADMIN') looks for. */
    private JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Login ignores any token, so a browser still holding an expired one can log in again. */
    private BearerTokenResolver tokenResolver() {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> LOGIN_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()))
                ? null
                : standard.resolve(request);
    }

    /** Only the React dev server may call the API from a browser on another origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${ota.security.cors-allowed-origin}") String origin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Lets the frontend read the file name of a downloaded invoice PDF
        cors.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
