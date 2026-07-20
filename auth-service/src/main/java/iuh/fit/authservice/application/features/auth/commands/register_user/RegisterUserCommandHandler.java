package iuh.fit.authservice.application.features.auth.commands.register_user;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.application.mapper.RegisterUserMapper;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.authservice.presentation.dto.event.UserRegisteredEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegisterUserCommandHandler {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    RegisterUserMapper registerUserMapper;
    KafkaTemplate kafkaTemplate;

    @Transactional
    public RegisterUserResult handle(RegisterUserCommand command) {
        if (userRepository.existsByEmail(command.getEmail())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User newUser = registerUserMapper.toEntityFromCommand(command);
        newUser.setPassword(passwordEncoder.encode(command.getPassword()));

        User savedUser = userRepository.save(newUser);

        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .dateOfBirth(command.getDateOfBirth())
                .gender(command.getGender())
                .build();
        kafkaTemplate.send("user.registered", event);

        return registerUserMapper.toDto(savedUser);
    }
}
