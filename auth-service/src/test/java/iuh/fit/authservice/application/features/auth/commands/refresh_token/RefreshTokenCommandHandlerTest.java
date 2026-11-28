package iuh.fit.authservice.application.features.auth.commands.refresh_token;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Làm mới token: xoay vòng refresh token nhưng không đăng xuất người dùng vì yêu cầu trùng")
class RefreshTokenCommandHandlerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final String FINGERPRINT = "fp-may-tinh";

    private UserRepository userRepository;
    private DeviceRepository deviceRepository;
    private TokenProvider tokenProvider;
    private JwtDecoder jwtDecoder;
    private JwtUtil jwtUtil;
    private RedisCacheService redis;
    private RefreshTokenCommandHandler handler;

    /** Bản ghi thiết bị trong "CSDL" giả lập. */
    private Device stored;
    /** Redis giả lập. */
    private final Map<String, Object> redisStore = new ConcurrentHashMap<>();
    private final AtomicInteger issued = new AtomicInteger();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        tokenProvider = mock(TokenProvider.class);
        jwtDecoder = mock(JwtDecoder.class);
        jwtUtil = mock(JwtUtil.class);
        redis = mock(RedisCacheService.class);
        handler = new RefreshTokenCommandHandler(userRepository, deviceRepository, tokenProvider, jwtDecoder, jwtUtil, redis);

        User user = new User();
        user.setId(USER_ID);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        stored = new Device();
        stored.setId(DEVICE_ID);
        stored.setUser(user);
        stored.setDeviceFingerprint(FINGERPRINT);
        stored.setStatus(DeviceStatus.ACTIVE);
        stored.setRefreshTokenHash("hash:R0");
        when(deviceRepository.findByUserIdAndDeviceFingerprint(USER_ID, FINGERPRINT)).thenAnswer(inv -> Optional.of(copy(stored)));
        when(deviceRepository.findByIdForUpdate(DEVICE_ID)).thenAnswer(inv -> Optional.of(copy(stored)));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            stored = copy(inv.getArgument(0));
            return inv.getArgument(0);
        });

        // Mọi chuỗi refresh token đều giải mã được thành JWT loại "refresh" của USER_ID
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> Jwt.withTokenValue(inv.getArgument(0))
                .header("alg", "RS256").subject(USER_ID.toString()).claim("type", "refresh")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        when(jwtUtil.extractTokenVersion(any())).thenReturn(1);
        when(jwtUtil.hashToken(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(tokenProvider.generateAccessToken(any(), any())).thenAnswer(inv -> "A" + issued.incrementAndGet());
        when(tokenProvider.generateRefreshToken(any(), any())).thenAnswer(inv -> "R" + issued.incrementAndGet());

        doAnswer(inv -> { redisStore.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(redis).set(anyString(), any(), any(Duration.class));
        when(redis.get(anyString(), eq(String.class))).thenAnswer(inv -> (String) redisStore.get(inv.getArgument(0)));
    }

    private static Device copy(Device d) {
        Device c = new Device();
        c.setId(d.getId());
        c.setUser(d.getUser());
        c.setDeviceFingerprint(d.getDeviceFingerprint());
        c.setStatus(d.getStatus());
        c.setRefreshTokenHash(d.getRefreshTokenHash());
        return c;
    }

    private RefreshTokenResult refresh(String refreshToken) {
        return handler.handle(RefreshTokenCommand.builder().refreshToken(refreshToken).deviceFingerprint(FINGERPRINT).build());
    }

    @Test
    @DisplayName("Làm mới bình thường: cấp token mới và xoay vòng refresh token")
    void normalRotation() {
        RefreshTokenResult result = refresh("R0");

        assertNotNull(result.getAccessToken());
        assertNotEquals("R0", result.getRefreshToken());
        assertEquals("hash:" + result.getRefreshToken(), stored.getRefreshTokenHash());
        assertEquals(DeviceStatus.ACTIVE, stored.getStatus());
    }

    @Test
    @DisplayName("Hai yêu cầu trùng cùng mang refresh token cũ: yêu cầu sau nhận lại ĐÚNG token mới, thiết bị không bị thu hồi")
    void duplicateRequestWithinGraceGetsSameNewToken() {
        RefreshTokenResult first = refresh("R0");
        RefreshTokenResult duplicate = refresh("R0");

        assertEquals(first.getRefreshToken(), duplicate.getRefreshToken(), "Hai phản hồi phải đặt cùng một cookie refresh token");
        assertNotNull(duplicate.getAccessToken());
        assertEquals(DeviceStatus.ACTIVE, stored.getStatus(), "Thiết bị không được bị thu hồi");
        assertEquals("hash:" + first.getRefreshToken(), stored.getRefreshTokenHash(), "Không xoay vòng thêm lần nữa");

        // Phiên tiếp tục bình thường với token mới
        RefreshTokenResult next = refresh(first.getRefreshToken());
        assertEquals(DeviceStatus.ACTIVE, stored.getStatus());
        assertEquals("hash:" + next.getRefreshToken(), stored.getRefreshTokenHash());
    }

    @Test
    @DisplayName("Khóa dòng thiết bị: refresh đọc thiết bị bằng SELECT ... FOR UPDATE để hai yêu cầu đồng thời xử lý lần lượt")
    void usesRowLock() {
        refresh("R0");
        verify(deviceRepository).findByIdForUpdate(DEVICE_ID);
    }

    @Test
    @DisplayName("Dùng lại refresh token cũ SAU thời gian ân hạn (không còn trong Redis): vẫn bị coi là tấn công và thu hồi thiết bị")
    void reuseAfterGraceRevokes() {
        refresh("R0");
        redisStore.clear(); // hết 30 giây ân hạn

        assertThrows(BusinessException.class, () -> refresh("R0"));
        assertEquals(DeviceStatus.REVOKED, stored.getStatus());
    }

    @Test
    @DisplayName("Redis lỗi khi đọc ân hạn: xử lý như trước (thu hồi), không làm hỏng luồng làm mới bình thường")
    void redisFailureDoesNotBreakNormalRefresh() {
        doThrow(new RuntimeException("Redis down")).when(redis).set(anyString(), any(), any(Duration.class));
        when(redis.get(anyString(), eq(String.class))).thenThrow(new RuntimeException("Redis down"));

        RefreshTokenResult result = refresh("R0");
        assertNotNull(result.getAccessToken());
        assertEquals(DeviceStatus.ACTIVE, stored.getStatus());
    }
}
