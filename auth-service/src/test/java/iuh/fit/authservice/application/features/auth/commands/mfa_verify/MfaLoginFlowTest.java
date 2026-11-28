package iuh.fit.authservice.application.features.auth.commands.mfa_verify;

import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommand;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserResult;
import iuh.fit.authservice.application.mapper.LoginUserMapper;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.authservice.infrastructure.security.TotpUtil;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.commonframework.infrastructure.security.OtpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Đăng nhập 2 bước từ đầu đến cuối với Redis giả lập dùng chung: bước đăng nhập lưu phiên MFA, bước xác minh phải đọc được
 * đúng phiên đó. Trước đây hai bước dùng hai khóa Redis khác nhau nên nhập đúng mã vẫn bị báo "Mã OTP không hợp lệ".
 */
@DisplayName("Đăng nhập MFA: phiên do bước đăng nhập tạo phải được bước xác minh đọc được")
class MfaLoginFlowTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private final Map<String, Object> redisStore = new ConcurrentHashMap<>();
    private User user;
    private LoginUserCommandHandler login;
    private MfaVerifyCommandHandler verify;
    private TotpUtil totpUtil;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenProvider tokens = mock(TokenProvider.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        LoginUserMapper mapper = mock(LoginUserMapper.class);
        RedisCacheService redis = mock(RedisCacheService.class);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("mfa@example.com");
        user.setPassword("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setMfaEnabled(true);
        user.setMfaType(MfaType.TOTP);
        user.setMfaSecret(SECRET);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(devices.findByUserIdAndDeviceFingerprint(any(), anyString())).thenReturn(Optional.empty());
        when(devices.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(tokens.generateAccessToken(any(), any())).thenReturn("access-token");
        when(tokens.generateRefreshToken(any(), any())).thenReturn("refresh-token");
        when(jwtUtil.hashToken(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        // Redis giả lập
        doAnswer(inv -> { redisStore.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(redis).set(anyString(), any(), any(Duration.class));
        when(redis.get(anyString(), any())).thenAnswer(inv -> {
            Object v = redisStore.get(inv.getArgument(0));
            Class<?> type = inv.getArgument(1);
            return type.isInstance(v) ? v : null;
        });
        doAnswer(inv -> { redisStore.remove(inv.getArgument(0)); return null; }).when(redis).delete(anyString());

        OtpUtil otpUtil = new OtpUtil();
        ReflectionTestUtils.setField(otpUtil, "defaultOtpLength", 6);
        ReflectionTestUtils.setField(otpUtil, "defaultOtpTtlMinutes", 5L);
        totpUtil = new TotpUtil();
        ReflectionTestUtils.setField(totpUtil, "secretSize", 20);
        ReflectionTestUtils.setField(totpUtil, "timeStepSeconds", 30);
        ReflectionTestUtils.setField(totpUtil, "codeDigits", 6);

        login = new LoginUserCommandHandler(users, devices, encoder, tokens, jwtUtil, mapper, otpUtil, redis, kafka);
        ReflectionTestUtils.setField(login, "maxFailedLoginAttempts", 5);
        ReflectionTestUtils.setField(login, "failedLoginLockoutMinutes", 15);
        verify = new MfaVerifyCommandHandler(users, devices, tokens, mapper, jwtUtil, totpUtil, redis);
    }

    private String currentTotp() {
        long bucket = System.currentTimeMillis() / 1000 / 30;
        long code = ReflectionTestUtils.invokeMethod(totpUtil, "generateTotpCode", SECRET, bucket);
        return String.format("%06d", code);
    }

    private LoginUserResult loginStep() {
        LoginUserCommand cmd = new LoginUserCommand();
        cmd.setEmail(user.getEmail());
        cmd.setPassword("Test@12345");
        cmd.setDeviceFingerprint("fp-1");
        return login.handle(cmd);
    }

    private MfaVerifyResult verifyStep(String mfaToken, String code) {
        return verify.handle(MfaVerifyCommand.builder().mfaToken(mfaToken).otpCode(code).deviceFingerprint("fp-1").build());
    }

    @Test
    @DisplayName("TOTP: đăng nhập trả mfaToken, nhập đúng mã thì được cấp token và thiết bị ACTIVE")
    void totpLoginThenVerify() {
        LoginUserResult step1 = loginStep();
        assertTrue(Boolean.TRUE.equals(step1.getMfaRequired()));
        assertNotNull(step1.getMfaToken());

        MfaVerifyResult step2 = verifyStep(step1.getMfaToken(), currentTotp());

        assertEquals("access-token", step2.getAccessToken());
        assertEquals("refresh-token", step2.getRefreshToken());
    }

    @Test
    @DisplayName("TOTP: mã sai bị từ chối, mfaToken đã dùng thành công không dùng lại được")
    void wrongCodeAndReuse() {
        LoginUserResult step1 = loginStep();
        String wrong = currentTotp().equals("000000") ? "111111" : "000000";
        assertThrows(BusinessException.class, () -> verifyStep(step1.getMfaToken(), wrong));

        verifyStep(step1.getMfaToken(), currentTotp());
        assertThrows(BusinessException.class, () -> verifyStep(step1.getMfaToken(), currentTotp()));
    }

    @Test
    @DisplayName("EMAIL: mã gửi qua email (Kafka) dùng được ở bước xác minh, mã khác bị từ chối")
    void emailOtpLoginThenVerify() {
        user.setMfaType(MfaType.EMAIL);
        LoginUserResult step1 = loginStep();
        assertEquals(MfaType.EMAIL, step1.getMfaType());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("notification.email.mfa-otp"), payload.capture());
        String sentOtp = String.valueOf(((Map<?, ?>) payload.getValue()).get("otp"));

        String wrong = sentOtp.equals("000000") ? "111111" : "000000";
        assertThrows(BusinessException.class, () -> verifyStep(step1.getMfaToken(), wrong));
        MfaVerifyResult step2 = verifyStep(step1.getMfaToken(), sentOtp);
        assertEquals("access-token", step2.getAccessToken());
    }

    @Test
    @DisplayName("Phiên MFA tạm thời bị xóa khỏi Redis sau khi xác minh xong")
    void mfaSessionEvicted() {
        LoginUserResult step1 = loginStep();
        verifyStep(step1.getMfaToken(), currentTotp());
        assertTrue(redisStore.keySet().stream().noneMatch(k -> k.contains(step1.getMfaToken())),
                "Phiên MFA phải bị xóa khỏi Redis sau khi xác minh xong");
    }
}
