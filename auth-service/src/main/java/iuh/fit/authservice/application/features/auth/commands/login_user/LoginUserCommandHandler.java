package iuh.fit.authservice.application.features.auth.commands.login_user;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.authservice.application.mapper.LoginUserMapper;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.commonframework.infrastructure.security.JwtUtil;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LoginUserCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    TokenProvider tokenProvider;
    LoginUserMapper loginUserMapper;
    JwtUtil jwtUtil;

    @Transactional
    public LoginUserResult handle(LoginUserCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(command.getPassword(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        Device device = deviceRepository.findByUserIdAndDeviceFingerprint(user.getId(), command.getDeviceFingerprint())
                .orElseGet(() -> {
                    Device newDevice = new Device();
                    newDevice.setUser(user);
                    newDevice.setDeviceFingerprint(command.getDeviceFingerprint());
                    return newDevice;
                });

        loginUserMapper.updateDevice(command, jwtUtil.hashToken(refreshToken), device);

        deviceRepository.save(device);

        return loginUserMapper.toLoginUserResult(accessToken, refreshToken, loginUserMapper.toDto(user));
    }
}
