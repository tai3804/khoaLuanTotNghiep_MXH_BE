package iuh.fit.userservice.infrastructure.kafka.consumers;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserProfileMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import iuh.fit.userservice.presentation.dto.event.UserRegisteredEvent;
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
    UserProfileMapper userProfileMapper;

    @KafkaListener(topics = "user.registered", groupId = "user-service-group")
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId: {}", event.getUserId());

        userProfileRepository.findByUserId(event.getUserId())
                .ifPresentOrElse(existingProfile -> {
                    log.info("UserProfile already exists for userId: {}. Updating with registration event data.", event.getUserId());
                    if (event.getFirstName() != null && !event.getFirstName().isBlank()) {
                        existingProfile.setFirstName(event.getFirstName());
                    }
                    if (event.getLastName() != null && !event.getLastName().isBlank()) {
                        existingProfile.setLastName(event.getLastName());
                    }
                    if (event.getMiddleName() != null) {
                        existingProfile.setMiddleName(event.getMiddleName());
                    }
                    if (event.getDateOfBirth() != null) {
                        existingProfile.setDateOfBirth(event.getDateOfBirth());
                    }
                    if (event.getGender() != null) {
                        existingProfile.setGender(userProfileMapper.mapGender(event.getGender()));
                    }
                    userProfileRepository.save(existingProfile);
                    log.info("Successfully merged and updated UserProfile for userId: {}", event.getUserId());
                }, () -> {
                    UserProfile newProfile = userProfileMapper.toEntity(event);
                    userProfileRepository.save(newProfile);
                    log.info("Successfully created UserProfile for userId: {}", event.getUserId());
                });
    }
}
