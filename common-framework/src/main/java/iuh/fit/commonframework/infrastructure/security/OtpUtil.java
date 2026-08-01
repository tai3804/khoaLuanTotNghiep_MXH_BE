package iuh.fit.commonframework.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

@Component
public class OtpUtil {

    @Value("${app.security.otp.length:6}")
    private int defaultOtpLength;

    @Value("${app.security.otp.ttl-minutes:5}")
    private long defaultOtpTtlMinutes;

    @Value("${app.security.otp.reset-ttl-minutes:15}")
    private long resetOtpTtlMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateOtp() {
        return generateOtp(defaultOtpLength);
    }

    public String generateOtp(int length) {
        if (length <= 0) {
            length = 6;
        }
        int max = (int) Math.pow(10, length);
        int number = RANDOM.nextInt(max);
        return String.format("%0" + length + "d", number);
    }

    public Duration getDefaultTtl() {
        return Duration.ofMinutes(defaultOtpTtlMinutes);
    }

    public Duration getResetPasswordTtl() {
        return Duration.ofMinutes(resetOtpTtlMinutes);
    }

    public Duration getTtlMinutes(long minutes) {
        return Duration.ofMinutes(minutes);
    }
}
