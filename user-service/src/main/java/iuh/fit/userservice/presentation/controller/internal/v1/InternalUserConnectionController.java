package iuh.fit.userservice.presentation.controller.internal.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal User Connections", description = "Internal APIs for feed fanout and audience discovery")
public class InternalUserConnectionController {

    UserConnectionRepository userConnectionRepository;

    @GetMapping("/{userId}/audience-ids")
    @Operation(summary = "Get all user IDs who should receive posts in their feeds (friends + followers + author)")
    public ResponseEntity<ApiResponse<List<UUID>>> getAudienceIds(@PathVariable UUID userId) {
        Set<UUID> audienceIds = new HashSet<>();
        audienceIds.add(userId);

        try {
            List<UserConnection> friends = userConnectionRepository.findAllAcceptedFriendships(userId);
            for (UserConnection conn : friends) {
                if (conn.getRequesterId() != null && !conn.getRequesterId().equals(userId)) {
                    audienceIds.add(conn.getRequesterId());
                } else if (conn.getTargetId() != null && !conn.getTargetId().equals(userId)) {
                    audienceIds.add(conn.getTargetId());
                }
            }

            List<UserConnection> followers = userConnectionRepository.findAllAcceptedFollowers(userId);
            for (UserConnection conn : followers) {
                if (conn.getRequesterId() != null) {
                    audienceIds.add(conn.getRequesterId());
                }
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(audienceIds), "Audience IDs retrieved successfully"));
    }
}

