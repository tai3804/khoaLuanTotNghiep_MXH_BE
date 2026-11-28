package iuh.fit.callservice.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cấp cấu hình ICE (STUN/TURN) cho WebRTC theo chuẩn TURN REST API của coturn")
class IceServerServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static String hmacSha1Base64(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Có khóa bí mật: cấp tài khoản TURN tạm thời username = hạn_dùng:userId, credential = HMAC-SHA1")
    void issuesTimeLimitedTurnCredentials() throws Exception {
        IceServerService service = new IceServerService(
                "stun:stun.l.google.com:19302",
                "turn:turn.example.com:3478?transport=udp, turn:turn.example.com:3478?transport=tcp",
                "bi-mat-dung-chung", 3600, FIXED);

        IceServerService.IceConfig config = service.issue(USER);

        assertEquals(3600, config.ttlSeconds());
        assertEquals(2, config.iceServers().size());
        assertEquals(List.of("stun:stun.l.google.com:19302"), config.iceServers().get(0).urls());
        assertNull(config.iceServers().get(0).username());

        IceServerService.IceServer turn = config.iceServers().get(1);
        assertEquals(List.of("turn:turn.example.com:3478?transport=udp", "turn:turn.example.com:3478?transport=tcp"), turn.urls());
        long expiry = Instant.parse("2026-11-27T10:00:00Z").getEpochSecond() + 3600;
        assertEquals(expiry + ":" + USER, turn.username());
        assertEquals(hmacSha1Base64("bi-mat-dung-chung", turn.username()), turn.credential());
    }

    @Test
    @DisplayName("Chưa cấu hình TURN (thiếu khóa hoặc địa chỉ): chỉ trả STUN, không cấp tài khoản")
    void onlyStunWhenTurnNotConfigured() {
        IceServerService noSecret = new IceServerService("stun:a:3478", "turn:t:3478", "", 3600, FIXED);
        IceServerService noUrls = new IceServerService("stun:a:3478", "", "bi-mat", 3600, FIXED);

        for (IceServerService service : List.of(noSecret, noUrls)) {
            IceServerService.IceConfig config = service.issue(USER);
            assertEquals(1, config.iceServers().size());
            assertTrue(config.iceServers().get(0).urls().get(0).startsWith("stun:"));
        }
    }
}
