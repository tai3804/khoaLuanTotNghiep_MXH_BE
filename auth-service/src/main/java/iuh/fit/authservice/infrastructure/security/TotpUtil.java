package iuh.fit.authservice.infrastructure.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

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
            return false;
        }

        int code;
        try {
            code = Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long currentBucket = System.currentTimeMillis() / 1000 / timeStepSeconds;

        // Window of -1, 0, +1 for clock drift compensation
        for (int i = -1; i <= 1; i++) {
            long hash = generateTotpCode(secret, currentBucket + i);
            if (hash == code) {
                return true;
            }
        }
        return false;
    }

    private long generateTotpCode(String secret, long timeBucket) {
        try {
            byte[] keyBytes = BASE32.decode(secret.toUpperCase());
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
            return -1;
        }
    }
}
