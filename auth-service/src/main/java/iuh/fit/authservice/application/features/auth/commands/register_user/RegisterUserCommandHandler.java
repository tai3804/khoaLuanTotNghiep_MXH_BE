package iuh.fit.authservice.application.features.auth.commands.register_user;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.application.mapper.RegisterUserMapper;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.authservice.presentation.dto.event.UserRegisteredEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegisterUserCommandHandler {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    RegisterUserMapper registerUserMapper;
    KafkaTemplate<String, Object> kafkaTemplate;
    RedisCacheService redisCacheService;

    @Transactional
    public RegisterUserResult handle(RegisterUserCommand command) {
        String normalizedEmail = command.getEmail() != null ? command.getEmail().trim().toLowerCase() : "";
        if (normalizedEmail.isEmpty()) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_SESSION_INVALID);
        }
        command.setEmail(normalizedEmail);

        String sessionToken = command.getRegisterSessionToken() != null ? command.getRegisterSessionToken().trim() : "";
        if (sessionToken.isEmpty()) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_SESSION_INVALID);
        }

        String sessionRedisKey = "register_session:" + sessionToken;
        String verifiedEmail;
        try {
            verifiedEmail = redisCacheService.get(sessionRedisKey, String.class);
        } catch (Exception e) {
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        if (verifiedEmail == null || !verifiedEmail.trim().equalsIgnoreCase(normalizedEmail)) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_SESSION_INVALID);
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (command.getDateOfBirth() == null) {
            throw new BusinessException(AuthErrorCode.DATE_OF_BIRTH_REQUIRED);
        }

        LocalDate now = LocalDate.now();
        if (command.getDateOfBirth().isAfter(now)) {
            throw new BusinessException(AuthErrorCode.INVALID_DATE_OF_BIRTH);
        }

        Period age = Period.between(command.getDateOfBirth(), now);
        if (age.getYears() < 13) {
            throw new BusinessException(AuthErrorCode.USER_UNDER_13);
        }

        User newUser = registerUserMapper.toEntityFromCommand(command);
        newUser.setEmail(normalizedEmail);
        newUser.setPassword(passwordEncoder.encode(command.getPassword()));

        User savedUser = userRepository.save(newUser);
        redisCacheService.delete(sessionRedisKey);

        UserRegisteredEvent event = registerUserMapper.toEvent(savedUser, command);
        kafkaTemplate.send("user.registered", event);

        return registerUserMapper.toDto(savedUser);
    }
}
