package iuh.fit.graduationthesis.common.utils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Slf4j
@Getter
@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class JwtUtil {
    @Value("${jwt.private-key-path}")
    String privateKeyPath;

    @Value("${jwt.public-key-path}")
    String publicKeyPath;

    @Value("${jwt.expiration-minute}")
    long expirationMinute;

    @Value("${jwt.refresh-token-expiration-day}")
    long refreshTokenExpirationDay;

    // Cache lại 2 key này trong RAM dưới dạng biến để các service khác gọi lấy ra ngay lập tức
    RSAPublicKey publicKey;
    RSAPrivateKey privateKey;

    /**
     * Chỉ đọc file 1 lần duy nhất khi Start Server.
     */
    @PostConstruct
    public void initKeys() {
        try {
            this.publicKey = loadPublicKey(publicKeyPath);
            this.privateKey = loadPrivateKey(privateKeyPath);
            log.info("[JwtUtil]: Cặp khóa RSA (Public/Private Key) đã được nạp thành công vào bộ nhớ!");
        } catch (Exception e) {
            log.error("[JwtUtil]: Khởi tạo cặp khóa thất bại!", e);
        }
    }

    /**
     * Sinh Refresh Token — chuỗi UUID ngẫu nhiên, không mang thông tin gì
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Nạp Public Key
     */
    public RSAPublicKey loadPublicKey(String path) {
        try {
            String pemContent = StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);

            RSAKey rsaKey = (RSAKey) RSAKey.parseFromPEMEncodedObjects(pemContent);
            return rsaKey.toRSAPublicKey();
        } catch (Exception e) {
            log.error("[Jwt Util]: Không thể load Public Key tại đường dẫn: {}", path, e);
            throw new RuntimeException("Lỗi khi load Public Key: " + e.getMessage(), e);
        }
    }

    /**
     * Nạp Private Key chuẩn Nimbus tối giản
     */
    public RSAPrivateKey loadPrivateKey(String path)  {
        try {
            String pemContent = StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);

            RSAKey rsaKey = (RSAKey) RSAKey.parseFromPEMEncodedObjects(pemContent);
            return rsaKey.toRSAPrivateKey();
        } catch (Exception e) {
            log.error("[Jwt Util]: Không thể load Private Key tại đường dẫn: {}", path, e);
            throw new RuntimeException("Lỗi khi load Private Key: " + e.getMessage(), e);
        }
    }

    /**
     * GIẢI MÃ
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (publicKey == null) {
            throw new IllegalStateException("Không thể load Public Key từ đường dẫn: " + publicKey);
        }
        return NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    /**
     * MÃ HÓA
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        if (publicKey == null || privateKey == null) {
            throw new IllegalStateException("Không thể load RSA keys. Public Key null: " + (publicKey == null)
                    + ", Private Key null: " + (privateKey == null));
        }

        JWK jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }
}