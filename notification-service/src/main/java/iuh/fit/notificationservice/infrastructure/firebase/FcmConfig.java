package iuh.fit.notificationservice.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.credentials-file:classpath:firebase-service-account.json}")
    private String credentialsFilePath;

    @Value("${fcm.enabled:true}")
    private boolean fcmEnabled;

    @Bean
    public FirebaseMessaging firebaseMessaging(ResourceLoader resourceLoader) {
        if (!fcmEnabled) {
            log.info("FCM Push Notifications are disabled via configuration (fcm.enabled=false).");
            return null;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                Resource resource = resourceLoader.getResource(credentialsFilePath);
                if (resource.exists()) {
                    try (InputStream serviceAccount = resource.getInputStream()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp.initializeApp(options);
                        log.info("FirebaseApp initialized successfully with credentials from: {}", credentialsFilePath);
                    }
                } else {
                    log.warn("FCM credentials file not found at: {}. FCM service initialized in mock/fallback mode.", credentialsFilePath);
                    return null;
                }
            }
            return FirebaseMessaging.getInstance();
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp: {}. FCM push notifications will be unavailable until valid service account JSON is provided.", e.getMessage());
            return null;
        }
    }
}
