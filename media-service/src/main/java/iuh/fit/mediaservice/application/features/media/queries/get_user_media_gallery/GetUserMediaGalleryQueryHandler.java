package iuh.fit.mediaservice.application.features.media.queries.get_user_media_gallery;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.presentation.dto.response.MediaResponse;
import iuh.fit.mediaservice.presentation.mapper.MediaPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserMediaGalleryQueryHandler {

    MediaRepository mediaRepository;
    JwtUtil jwtUtil;
    MediaPresentationMapper mediaPresentationMapper;

    public PagedResponse<MediaResponse> handle(GetUserMediaGalleryQuery query) {
        String userIdParam = query.getUserId();
        int page = Math.max(0, query.getPage());
        int size = query.getSize() > 0 ? Math.min(query.getSize(), 100) : 20;

        UUID currentUserUuid = null;
        String currentUserId = jwtUtil.getCurrentUserId();
        if (currentUserId != null && !currentUserId.isBlank()) {
            try {
                currentUserUuid = UUID.fromString(currentUserId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        UUID targetUserId = null;
        if (userIdParam == null || "me".equalsIgnoreCase(userIdParam) || userIdParam.isBlank()) {
            targetUserId = currentUserUuid;
        } else {
            try {
                targetUserId = UUID.fromString(userIdParam);
            } catch (Exception e) {
                targetUserId = currentUserUuid;
            }
        }

        if (targetUserId == null) {
            return PagedResponse.from(Page.empty(), List.of());
        }

        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean isStaff = roles != null && roles.stream().anyMatch(r ->
                "ROLE_ADMIN".equalsIgnoreCase(r) ||
                "ADMIN".equalsIgnoreCase(r) ||
                "ROLE_MODERATOR".equalsIgnoreCase(r) ||
                "MODERATOR".equalsIgnoreCase(r)
        );
        boolean isOwner = targetUserId.equals(currentUserUuid);

        Page<Media> mediaPage = (isOwner || isStaff)
                ? mediaRepository.findByUserIdOrderByCreatedAtDesc(targetUserId, PageRequest.of(page, size))
                : mediaRepository.findPublicGalleryByUserId(targetUserId, PageRequest.of(page, size));

        List<MediaResponse> responseList = mediaPage.getContent().stream()
                .map(mediaPresentationMapper::toResponse)
                .toList();

        return PagedResponse.from(mediaPage, responseList);
    }
}
