package iuh.fit.feedservice.application.features.feed.commands.process_post_created;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import iuh.fit.feedservice.infrastructure.client.UserServiceClient;
import iuh.fit.feedservice.infrastructure.persistence.repository.UserFeedItemRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessPostCreatedCommandHandler {

    UserFeedItemRepository userFeedItemRepository;
    UserServiceClient userServiceClient;

    @Transactional
    public void handle(ProcessPostCreatedCommand command) {
        LocalDateTime createdAt = command.getCreatedAt() != null ? command.getCreatedAt() : LocalDateTime.now();

        Set<UUID> recipientUserIds = new HashSet<>();
        recipientUserIds.add(command.getAuthorId()); // Add author's own timeline

        try {
            ApiResponse<PagedResponse<UserServiceClient.UserConnectionDto>> response = userServiceClient.getFollowers(0, 1000);
            if (response != null && response.getData() != null && response.getData().getContent() != null) {
                for (UserServiceClient.UserConnectionDto conn : response.getData().getContent()) {
                    if (conn.getUserId() != null) {
                        recipientUserIds.add(conn.getUserId());
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback if user-service is offline or call fails
        }

        List<UserFeedItem> feedItems = recipientUserIds.stream()
                .map(userId -> UserFeedItem.builder()
                        .userId(userId)
                        .postId(command.getPostId())
                        .authorId(command.getAuthorId())
                        .createdAt(createdAt)
                        .build())
                .toList();

        userFeedItemRepository.saveAll(feedItems);
    }
}
