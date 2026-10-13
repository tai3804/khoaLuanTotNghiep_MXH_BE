package iuh.fit.mediaservice.application.features.media.queries.get_user_quota;

import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.presentation.dto.response.UserStorageQuotaResponse;
import iuh.fit.mediaservice.presentation.mapper.MediaPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserQuotaQueryHandler {

    MediaRepository mediaRepository;
    JwtUtil jwtUtil;
    MediaPresentationMapper mediaPresentationMapper;

    public UserStorageQuotaResponse handle() {
        String userIdStr = jwtUtil.getCurrentUserId();
        UUID userId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        long usedBytes = userId != null ? mediaRepository.sumFileSizeByUserId(userId) : 0;
        long maxQuotaBytes = 1073741824L; // 1 GB Quota

        return mediaPresentationMapper.toQuotaResponse(userId, usedBytes, maxQuotaBytes);
    }
}
