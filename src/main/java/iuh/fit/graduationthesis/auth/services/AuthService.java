package iuh.fit.graduationthesis.auth.services;

import iuh.fit.graduationthesis.auth.dto.AuthResponse;
import iuh.fit.graduationthesis.auth.dto.LoginRequest;
import iuh.fit.graduationthesis.auth.dto.RegisterRequest;
import iuh.fit.graduationthesis.auth.modules.Account;
import iuh.fit.graduationthesis.auth.modules.Permission;
import iuh.fit.graduationthesis.auth.modules.RefreshToken;
import iuh.fit.graduationthesis.auth.modules.enums.Role;
import iuh.fit.graduationthesis.auth.repositories.AccountRepository;
import iuh.fit.graduationthesis.auth.repositories.PermissionRepository;
import iuh.fit.graduationthesis.auth.repositories.RefreshTokenRepository;
import iuh.fit.graduationthesis.common.exceptions.ErrorCode;
import iuh.fit.graduationthesis.common.exceptions.exception_types.BusinessException;
import iuh.fit.graduationthesis.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    AccountRepository accountRepository;
    PermissionRepository permissionRepository;
    RefreshTokenRepository refreshTokenRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;
    JwtUtil jwtUtil;

    /**
     * Đăng ký tài khoản mới
     */
    @Transactional("authTransactionManager")
    public AuthResponse register(RegisterRequest request) {
        // 1. Kiểm tra username đã tồn tại chưa
        if (accountRepository.existsByUserName(request.getUserName())) {
            throw new BusinessException(ErrorCode.USER_EXISTED);
        }

        // 2. Tìm hoặc tạo permission mặc định
        Permission defaultPermission = permissionRepository.findByName("READ_PRIVILEGES")
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                                .name("READ_PRIVILEGES")
                                .description("Quyền đọc cơ bản")
                                .build()
                ));

        // 3. Tạo Account
        Account account = Account.builder()
                .userName(request.getUserName())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(Role.USER))
                .permissions(Set.of(defaultPermission))
                .userId(UUID.randomUUID())
                .build();

        accountRepository.save(account);

        log.info("[AuthService]: Đăng ký thành công cho user: {}", account.getUserName());

        return AuthResponse.builder()
                .userName(account.getUserName())
                .build();
    }

    /**
     * Đăng nhập
     * - Xác thực username + password
     * - Sinh Access Token (JWT) + Refresh Token (UUID)
     * - Lưu Refresh Token vào DB
     */
    @Transactional("authTransactionManager")
    public AuthResponse login(LoginRequest request, String clientIp) {
        // 1. Tìm account theo username
        Account account = accountRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. So sánh password
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Chuyển roles và permissions sang Set<String> cho JWT
        Set<String> roles = account.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        Set<String> permissions = account.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        // 4. Sinh Access Token
        String accessToken = jwtService.generateToken(
                account.getUserId().toString(), roles, permissions, clientIp);

        // 5. Sinh + lưu Refresh Token
        String refreshTokenValue = jwtUtil.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .accountId(account.getId())
                .expiresAt(Instant.now().plus(jwtUtil.getRefreshTokenExpirationDay(), ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("[AuthService]: Đăng nhập thành công cho user: {}", account.getUserName());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshTokenValue)
                .userName(account.getUserName())
                .build();
    }
}
