package iuh.fit.notificationservice.infrastructure.firebase;

import com.google.firebase.messaging.*;
import iuh.fit.notificationservice.domain.entities.UserFcmToken;
import iuh.fit.notificationservice.domain.repositories.UserFcmTokenRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmPushNotificationService {

    UserFcmTokenRepository tokenRepository;
    Optional<FirebaseMessaging> firebaseMessaging;

    @Transactional
    public int sendPushNotification(UUID targetUserId, String title, String body, Map<String, String> dataPayload) {
        List<UserFcmToken> tokens = tokenRepository.findByUserId(targetUserId);
        if (tokens.isEmpty()) {
            log.info("No FCM tokens found for user: {}", targetUserId);
            return 0;
        }

        if (firebaseMessaging.isEmpty()) {
            log.warn("FirebaseMessaging is not initialized. Skipping push notification to user {}: title='{}'", targetUserId, title);
            return 0;
        }

        int successCount = 0;
        List<UserFcmToken> invalidTokens = new ArrayList<>();

        for (UserFcmToken fcmToken : tokens) {
            try {
                Notification notification = Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build();

                Message.Builder messageBuilder = Message.builder()
                        .setToken(fcmToken.getFcmToken())
                        .setNotification(notification);

                if (dataPayload != null && !dataPayload.isEmpty()) {
                    messageBuilder.putAllData(dataPayload);
                }

                // WebPush specific configuration for browser notifications
                WebpushConfig webpushConfig = WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .setIcon("/logo.png")
                                .setRequireInteraction(true)
                                .build())
                        .build();
                messageBuilder.setWebpushConfig(webpushConfig);

                String response = firebaseMessaging.get().send(messageBuilder.build());
                log.info("FCM push sent successfully to token {}: responseId={}", fcmToken.getFcmToken(), response);
                successCount++;
            } catch (FirebaseMessagingException e) {
                log.error("FCM push error for token {} (user {}): code={}, message={}",
                        fcmToken.getFcmToken(), targetUserId, e.getMessagingErrorCode(), e.getMessage());

                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                    e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalidTokens.add(fcmToken);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM push notification to token {}: {}", fcmToken.getFcmToken(), e.getMessage());
            }
        }

        if (!invalidTokens.isEmpty()) {
            log.info("Removing {} stale/invalid FCM tokens for user {}", invalidTokens.size(), targetUserId);
            tokenRepository.deleteAll(invalidTokens);
        }

        return successCount;
    }

    @Transactional
    public int sendMulticastNotification(List<UUID> targetUserIds, String title, String body, Map<String, String> dataPayload) {
        if (targetUserIds == null || targetUserIds.isEmpty()) return 0;

        int totalSuccess = 0;
        for (UUID userId : targetUserIds) {
            totalSuccess += sendPushNotification(userId, title, body, dataPayload);
        }
        return totalSuccess;
    }
}
