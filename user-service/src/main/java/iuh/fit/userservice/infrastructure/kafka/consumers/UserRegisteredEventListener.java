package iuh.fit.userservice.infrastructure.kafka.consumers;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.presentation.dto.event.UserRegisteredEvent;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import iuh.fit.userservice.infrastructure.persistence.models.UserProfileDbModel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserRegisteredEventListener {

    UserProfileRepository userProfileRepository;

    @KafkaListener(topics = "user.registered", groupId = "user-service-group")
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId: {}", event.getUserId());

        if (userProfileRepository.existsByUserId(event.getUserId())) {
            log.error("Profile already exists for userId: {}", event.getUserId());
            throw new BusinessException(UserServiceErrorCode.PROFILE_ALREADY_EXISTS);
        }

        iuh.fit.userservice.domain.enums.Gender parsedGender = null;
        if (event.getGender() != null && !event.getGender().isEmpty()) {
            try {
                parsedGender = iuh.fit.userservice.domain.enums.Gender.valueOf(event.getGender().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid gender value received: {}, defaulting to null", event.getGender());
            }
        }

        UserProfileDbModel newProfile = UserProfileDbModel.builder()
                .userId(event.getUserId())
                .dateOfBirth(event.getDateOfBirth())
                .gender(parsedGender)
                .build();

        userProfileRepository.save(newProfile);
        log.info("Successfully created UserProfile for userId: {}", event.getUserId());
    }
}
