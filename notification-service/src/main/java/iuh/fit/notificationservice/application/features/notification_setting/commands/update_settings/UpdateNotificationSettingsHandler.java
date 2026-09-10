package iuh.fit.notificationservice.application.features.notification_setting.commands.update_settings;

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
public class UpdateNotificationSettingsHandler {

    NotificationSettingRepository notificationSettingRepository;

    @Transactional
    public NotificationSetting handle(UpdateNotificationSettingsCommand command) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(command.getUserId())
                .orElseGet(() -> NotificationSetting.builder()
                        .userId(command.getUserId())
                        .build());

        if (command.getLikePost() != null) setting.setLikePost(command.getLikePost());
        if (command.getCommentPost() != null) setting.setCommentPost(command.getCommentPost());
        if (command.getSharePost() != null) setting.setSharePost(command.getSharePost());
        if (command.getFriendRequest() != null) setting.setFriendRequest(command.getFriendRequest());
        if (command.getMessage() != null) setting.setMessage(command.getMessage());
        if (command.getCall() != null) setting.setCall(command.getCall());
        if (command.getSystem() != null) setting.setSystem(command.getSystem());
        if (command.getSound() != null) setting.setSound(command.getSound());
        if (command.getEmailNotification() != null) setting.setEmailNotification(command.getEmailNotification());

        return notificationSettingRepository.save(setting);
    }
}
