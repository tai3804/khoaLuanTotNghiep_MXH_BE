package iuh.fit.aiservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ghi đè securityFilterChain của common-framework (tránh UnreachableFilterChainException) nhưng vẫn bắt buộc JWT:
 * - Từ điển từ khoá nhạy cảm, nhật ký và thống kê kiểm duyệt AI chỉ dành cho ADMIN/MODERATOR (chứa nội dung bài và mã tác giả).
 * - Các API còn lại (trợ lý AI, kiểm tra nội dung) yêu cầu người dùng đã đăng nhập, tránh bị lạm dụng quota Gemini.
 * Dùng lại JwtDecoder/JwtAuthenticationConverter của common-framework (RS256, kiểm tra token bị thu hồi qua Redis).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AiSecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                        JwtDecoder jwtDecoder,
                        JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/actuator/**")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/api/v1/ai/keywords",
                                                                "/api/v1/ai/keywords/**",
                                                                "/api/v1/ai/moderation/logs",
                                                                "/api/v1/ai/moderation/stats")
                                                .hasAnyRole("ADMIN", "MODERATOR")
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                                jwt -> jwt.decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

                return http.build();
        }
}
