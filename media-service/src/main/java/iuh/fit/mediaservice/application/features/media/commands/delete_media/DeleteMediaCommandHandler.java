package iuh.fit.mediaservice.application.features.media.commands.delete_media;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.InternalApiKeyFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.mediaservice.application.exception.MediaServiceErrorCode;
import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMediaCommandHandler {

    final AwsS3StorageService awsS3StorageService;
    final MediaRepository mediaRepository;
    final JwtUtil jwtUtil;

    @Value("${app.security.internal-api-key:kltn-internal-dev-key}")
    String internalApiKey;

    @Transactional
    public void handle(DeleteMediaCommand command) {
        if (command == null || command.getFileKey() == null || command.getFileKey().isBlank()) {
            return;
        }

        String rawKey = command.getFileKey().trim();
        String actualKey = awsS3StorageService.extractFileKeyFromUrl(rawKey);

        // 1. Kiểm tra xem request có mang X-Internal-Key hợp lệ hoặc ROLE_INTERNAL (gọi nội bộ giữa các microservices) không
        boolean isInternalCall = hasInternalAuthority();
        if (!isInternalCall) {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String providedKey = request.getHeader(InternalApiKeyFilter.HEADER);
                    if (providedKey != null && internalApiKey != null && !internalApiKey.isBlank()
                            && MessageDigest.isEqual(
                                    providedKey.getBytes(StandardCharsets.UTF_8),
                                    internalApiKey.getBytes(StandardCharsets.UTF_8))) {
                        isInternalCall = true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Kiểm tra quyền Admin/Moderator hoặc quyền sở hữu file (fail-closed) nếu không phải Internal Call
        if (!isInternalCall) {
            List<String> roles = jwtUtil.getCurrentUserRoles();
            boolean isAdmin = roles != null && (
                    roles.contains("ROLE_ADMIN") ||
                    roles.contains("ADMIN") ||
                    roles.contains("ROLE_MODERATOR") ||
                    roles.contains("MODERATOR")
            );

            if (!isAdmin) {
                String currentUserIdStr = jwtUtil.getCurrentUserId();
                if (currentUserIdStr == null || currentUserIdStr.isBlank()) {
                    throw new BusinessException(MediaServiceErrorCode.UNAUTHORIZED);
                }

                UUID currentUserId;
                try {
                    currentUserId = UUID.fromString(currentUserIdStr);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(MediaServiceErrorCode.UNAUTHORIZED);
                }

                Optional<Media> mediaOpt = mediaRepository.findByFileKey(actualKey);
                if (mediaOpt.isEmpty() && !actualKey.equals(rawKey)) {
                    mediaOpt = mediaRepository.findByFileKey(rawKey);
                }

                if (mediaOpt.isEmpty()) {
                    log.warn("Fail-closed BOLA protection: User {} attempted to delete untracked or non-existent media {}",
                            currentUserId, actualKey);
                    throw new BusinessException(MediaServiceErrorCode.FORBIDDEN_ACTION);
                }

                Media media = mediaOpt.get();
                if (media.getUserId() == null || !media.getUserId().equals(currentUserId)) {
                    log.warn("BOLA attempt blocked: User {} attempted to delete media {} owned by {}",
                            currentUserId, actualKey, media.getUserId());
                    throw new BusinessException(MediaServiceErrorCode.FORBIDDEN_ACTION);
                }
            }
        }

        awsS3StorageService.deleteFile(rawKey);
        mediaRepository.deleteByFileKey(actualKey);
        if (!actualKey.equals(rawKey)) {
            mediaRepository.deleteByFileKey(rawKey);
        }
    }

    private boolean hasInternalAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_INTERNAL".equals(a.getAuthority()) || "INTERNAL".equals(a.getAuthority()));
    }
}
