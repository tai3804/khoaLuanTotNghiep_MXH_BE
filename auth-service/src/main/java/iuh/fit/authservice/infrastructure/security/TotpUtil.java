package iuh.fit.authservice.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Slf4j
@Component
public class TotpUtil {

    @Value("${app.mfa.totp.secret-size:20}")
    private int secretSize;

    @Value("${app.mfa.totp.time-step-seconds:30}")
    private int timeStepSeconds;

    @Value("${app.mfa.totp.code-digits:6}")
    private int codeDigits;

    @Value("${app.mfa.totp.issuer:SocialNetworkApp}")
    private String defaultIssuer;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base32 BASE32 = new Base32();

    public String generateSecretKey() {
        byte[] bytes = new byte[secretSize];
        RANDOM.nextBytes(bytes);
        return BASE32.encodeToString(bytes).replace("=", "");
    }

    public String getQrCodeUrl(String email, String secret, String issuer) {
        String targetIssuer = (issuer != null && !issuer.isBlank()) ? issuer : defaultIssuer;
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String encodedIssuer = URLEncoder.encode(targetIssuer, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedEmail, secret, encodedIssuer);
    }

    public boolean verifyCode(String secret, String codeStr) {
        if (secret == null || codeStr == null || codeStr.isBlank()) {
            log.warn("TOTP Verification failed: secret or codeStr is null/blank");
            return false;
        }

        int code;
        try {
            code = Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            log.warn("TOTP Verification failed: code [{}] is not numeric", codeStr);
            return false;
        }

        long currentBucket = System.currentTimeMillis() / 1000 / timeStepSeconds;

        // Window of -2, -1, 0, +1, +2 for clock drift compensation (±60 seconds)
        for (int i = -2; i <= 2; i++) {
            long hash = generateTotpCode(secret, currentBucket + i);
            if (hash == code) {
                log.info("TOTP Verification succeeded for bucket offset {}", i);
                return true;
            }
        }
        log.warn("TOTP Verification failed for code [{}] against secret [{}]", codeStr, secret);
        return false;
    }

    private long generateTotpCode(String secret, long timeBucket) {
        try {
            String s = secret.toUpperCase().trim();
            while (s.length() % 8 != 0) {
                s += "=";
            }
            byte[] keyBytes = BASE32.decode(s);
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (timeBucket & 0xFF);
                timeBucket >>= 8;
            }

            SecretKeySpec signKey = new SecretKeySpec(keyBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) << 8) |
                    (hash[offset + 3] & 0xFF);

            return binary % (long) Math.pow(10, codeDigits);
        } catch (Exception e) {
            log.error("Error generating TOTP code from secret: {}", e.getMessage(), e);
            return -1;
        }
    }
}
