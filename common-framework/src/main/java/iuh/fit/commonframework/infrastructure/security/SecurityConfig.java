package iuh.fit.commonframework.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebSecurity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SecurityConfig {

        @Value("${app.security.jwt.public-key}")
        RSAPublicKey publicKey;

        final String[] PUBLIC_ENDPOINTS = {
                        "/api/v1/public/**",
                        "/api/v1/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/**",
        };

        /**
         * Cấu hình SecurityFilterChain với JWT.
         * 
         * @ConditionalOnMissingBean cho phép service tự override nếu cần cấu hình
         *                           riêng.
         */
        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                                                jwt -> jwt.decoder(jwtDecoder())));
                return http.build();
        }

        /**
         * Cấu hình JwtDecoder với RS256.
         * 
         * @ConditionalOnMissingBean cho phép service tự override nếu cần.
         */
        @Bean
        @ConditionalOnMissingBean(JwtDecoder.class)
        public JwtDecoder jwtDecoder() {
                return NimbusJwtDecoder.withPublicKey(publicKey).build();
        }
}
