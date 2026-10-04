package iuh.fit.commonframework.infrastructure.security;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SecurityConfig {

        @Value("${app.security.jwt.public-key}")
        RSAPublicKey publicKey;

        final String[] PUBLIC_ENDPOINTS = {
                        "/api/v1/public/**",
                        "/api/v1/internal/**",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/register/**",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/password/forgot",
                        "/api/v1/auth/password/reset",
                        "/api/v1/auth/mfa/verify",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/**",
                        "/api/v1/posts",
                        "/api/v1/posts/**",
                        "/api/v1/media/files/**",
                        "/api/v1/admin/settings/public",
                        "/api/v1/admin/settings/public/**",
                        "/api/v1/ai/**",
                        "/ws-chat",
                        "/ws-chat/**",
                        "/ws-notifications",
                        "/ws-notifications/**",
                        "/ws-call",
                        "/ws-call/**"
        };

        /**
         * Cấu hình SecurityFilterChain với JWT dùng chung cho toàn bộ hệ thống
         * microservices.
         */
        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        public SecurityFilterChain securityFilterChain(HttpSecurity http, RedisCacheService redisCacheService) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                                jwt -> jwt.decoder(jwtDecoder(redisCacheService))
                                                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
                return http.build();
        }

        /**
         * Cấu hình JwtDecoder với RS256 và chặn token hết hạn / revoked.
         */
        @Bean
        @ConditionalOnMissingBean(JwtDecoder.class)
        public JwtDecoder jwtDecoder(RedisCacheService redisCacheService) {
                NimbusJwtDecoder defaultDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                return token -> {
                        Jwt jwt = defaultDecoder.decode(token);
                        
                        String subject = jwt.getSubject();
                        if (subject != null) {
                                try {
                                        UUID userId = UUID.fromString(subject);
                                        Object tokenVersionObj = jwt.getClaim("tokenVersion");
                                        int tokenVersion = 1;
                                        if (tokenVersionObj instanceof Number) {
                                                tokenVersion = ((Number) tokenVersionObj).intValue();
                                        } else if (tokenVersionObj instanceof String) {
                                                try {
                                                        tokenVersion = Integer.parseInt((String) tokenVersionObj);
                                                } catch (NumberFormatException ignored) {}
                                        }
                                        
                                        if (redisCacheService.isTokenRevoked(userId, tokenVersion)) {
                                                throw new JwtValidationException("Token has been revoked (User Banned or Logged Out)", List.of());
                                        }
                                } catch (Exception e) {
                                        if (e instanceof JwtValidationException) {
                                                throw e;
                                        }
                                        log.error("Error during JWT validation: {}", e.getMessage());
                                }
                        }
                        return jwt;
                };
        }

        /**
         * Cấu hình JwtAuthenticationConverter để map claim "roles" và "permissions"
         * thành các quyền hạn Spring Security (hỗ trợ cả tiền tố ROLE_ và không có ROLE_).
         */
        @Bean
        @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        Set<GrantedAuthority> authorities = new HashSet<>();

                        // 1. Extract and map "roles" claim
                        Object rolesObj = jwt.getClaim("roles");
                        if (rolesObj instanceof Collection<?> rolesList) {
                                for (Object r : rolesList) {
                                        if (r != null) {
                                                String roleStr = r.toString().trim();
                                                if (!roleStr.isEmpty()) {
                                                        if (roleStr.startsWith("ROLE_")) {
                                                                authorities.add(new SimpleGrantedAuthority(roleStr));
                                                                authorities.add(new SimpleGrantedAuthority(roleStr.substring(5)));
                                                        } else {
                                                                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleStr));
                                                                authorities.add(new SimpleGrantedAuthority(roleStr));
                                                        }
                                                }
                                        }
                                }
                        }

                        // 2. Extract and map "permissions" claim
                        Object permissionsObj = jwt.getClaim("permissions");
                        if (permissionsObj instanceof Collection<?> permsList) {
                                for (Object p : permsList) {
                                        if (p != null) {
                                                String permStr = p.toString().trim();
                                                if (!permStr.isEmpty()) {
                                                        authorities.add(new SimpleGrantedAuthority(permStr));
                                                }
                                        }
                                }
                        }

                        // 3. Fallback for "scope" or "scp" claim if roles is empty
                        if (authorities.isEmpty()) {
                                Object scopeObj = jwt.getClaim("scope");
                                if (scopeObj instanceof String scopeStr) {
                                        for (String s : scopeStr.split(" ")) {
                                                if (!s.isBlank()) {
                                                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
                                                }
                                        }
                                }
                        }

                        return authorities;
                });
                return jwtAuthenticationConverter;
        }
}
