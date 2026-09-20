package iuh.fit.authservice.infrastructure.config;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.presentation.dto.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.admin.default-email:admin}")
    private String adminEmail;

    @Value("${app.admin.default-password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking if default Admin account exists with identifier: {}", adminEmail);

        Optional<User> existingUserOpt = userRepository.findByEmail(adminEmail);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            existingUser.setPassword(passwordEncoder.encode(adminPassword));
            existingUser.setStatus(UserStatus.ACTIVE);
            existingUser.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
            userRepository.save(existingUser);
            log.info("Default Admin account ({}) exists and password has been synced to '{}'.", adminEmail, adminPassword);
            return;
        }

        User adminUser = User.builder()
                .id(null)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .firstName("Admin")
                .lastName("Hệ Thống")
                .middleName("")
                .status(UserStatus.ACTIVE)
                .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
                .permissions(Set.of("ALL", "ADMIN_ACCESS", "MODERATION_ACCESS"))
                .mfaEnabled(false)
                .mfaType(MfaType.NONE)
                .tokenVersion(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build();

        User savedAdmin = userRepository.save(adminUser);
        log.info("Default Admin account created successfully! ID: {}, Identifier: {}, Password: {}", savedAdmin.getId(), adminEmail, adminPassword);

        // Broadcast UserRegisteredEvent for UserProfile creation in user-service
        try {
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .userId(savedAdmin.getId())
                    .email(savedAdmin.getEmail())
                    .firstName(savedAdmin.getFirstName())
                    .lastName(savedAdmin.getLastName())
                    .middleName(savedAdmin.getMiddleName())
                    .dateOfBirth(LocalDate.of(2000, 1, 1))
                    .gender("OTHER")
                    .build();

            kafkaTemplate.send("user.registered", event);
            log.info("Sent user.registered event for Admin userId: {}", savedAdmin.getId());
        } catch (Exception e) {
            log.warn("Could not publish user.registered Kafka event for admin: {}", e.getMessage());
        }
    }
}
