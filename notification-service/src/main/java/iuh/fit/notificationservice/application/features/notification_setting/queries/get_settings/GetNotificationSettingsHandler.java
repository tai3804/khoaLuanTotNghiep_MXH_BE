package iuh.fit.notificationservice.application.features.notification_setting.queries.get_settings;

import iuh.fit.notificationservice.domain.entities.NotificationSetting;
import iuh.fit.notificationservice.domain.repositories.NotificationSettingRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetNotificationSettingsHandler {

    NotificationSettingRepository notificationSettingRepository;

    @Transactional
    public NotificationSetting handle(GetNotificationSettingsQuery query) {
        return notificationSettingRepository.findByUserId(query.getUserId())
                .orElseGet(() -> {
                    NotificationSetting defaultSetting = NotificationSetting.builder()
                            .userId(query.getUserId())
                            .likePost(true)
                            .commentPost(true)
                            .sharePost(true)
                            .friendRequest(true)
                            .message(true)
                            .call(true)
                            .system(true)
                            .sound(true)
                            .emailNotification(true)
                            .build();
                    return notificationSettingRepository.save(defaultSetting);
                });
    }
}
