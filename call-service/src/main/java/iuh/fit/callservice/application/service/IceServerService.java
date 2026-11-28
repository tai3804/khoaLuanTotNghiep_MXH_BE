package iuh.fit.callservice.application.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Cấp cấu hình ICE (STUN + TURN) cho WebRTC của cuộc gọi và phát trực tiếp.
 *
 * TURN dùng cơ chế "TURN REST API" của coturn ({@code use-auth-secret}): call-service và coturn dùng chung một khóa bí mật;
 * mỗi người dùng nhận một tài khoản tạm với username = {@code <thời điểm hết hạn>:<userId>} và
 * credential = Base64(HMAC-SHA1(khóa bí mật, username)). coturn tự kiểm tra chữ ký và hạn dùng, nên không phải lưu
 * mật khẩu cố định ở trình duyệt và tài khoản tự vô hiệu khi hết hạn.
 *
 * Thiếu khóa bí mật hoặc địa chỉ TURN thì chỉ trả về STUN (kết nối trực tiếp, không trung chuyển).
 */
@Service
public class IceServerService {

    private final List<String> stunUrls;
    private final List<String> turnUrls;
    private final String turnSecret;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public IceServerService(
            @Value("${webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String stunUrls,
            @Value("${webrtc.turn.urls:}") String turnUrls,
            @Value("${webrtc.turn.secret:}") String turnSecret,
            @Value("${webrtc.turn.ttl-seconds:43200}") long ttlSeconds) {
        this(stunUrls, turnUrls, turnSecret, ttlSeconds, Clock.systemUTC());
    }

    IceServerService(String stunUrls, String turnUrls, String turnSecret, long ttlSeconds, Clock clock) {
        this.stunUrls = splitUrls(stunUrls);
        this.turnUrls = splitUrls(turnUrls);
        this.turnSecret = turnSecret == null ? "" : turnSecret.trim();
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 43200;
        this.clock = clock;
    }

    public IceConfig issue(UUID userId) {
        List<IceServer> servers = new ArrayList<>();
        if (!stunUrls.isEmpty()) {
            servers.add(new IceServer(stunUrls, null, null));
        }
        if (!turnUrls.isEmpty() && !turnSecret.isEmpty()) {
            long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
            String username = expiresAt + ":" + userId;
            servers.add(new IceServer(turnUrls, username, sign(username)));
        }
        return new IceConfig(servers, ttlSeconds);
    }

    public boolean isTurnEnabled() {
        return !turnUrls.isEmpty() && !turnSecret.isEmpty();
    }

    private String sign(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không tạo được chữ ký TURN", e);
        }
    }

    private static List<String> splitUrls(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Một máy chủ ICE theo định dạng RTCIceServer của trình duyệt. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(List<String> urls, String username, String credential) {}

    /** Danh sách máy chủ ICE và thời gian hiệu lực (giây) của tài khoản TURN. */
    public record IceConfig(List<IceServer> iceServers, long ttlSeconds) {}
}
