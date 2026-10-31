package iuh.fit.feedservice.application.features.feed.commands.process_post_created;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import iuh.fit.feedservice.infrastructure.client.UserServiceClient;
import iuh.fit.feedservice.infrastructure.persistence.repository.UserFeedItemRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessPostCreatedCommandHandler {

    UserFeedItemRepository userFeedItemRepository;
    UserServiceClient userServiceClient;

    @Transactional
    public void handle(ProcessPostCreatedCommand command) {
        if (command.getPostId() == null || command.getAuthorId() == null) {
            return;
        }

        LocalDateTime createdAt = command.getCreatedAt() != null ? command.getCreatedAt() : LocalDateTime.now();
        Set<UUID> recipientUserIds = new HashSet<>();
        recipientUserIds.add(command.getAuthorId());

        try {
            ApiResponse<List<UUID>> response = userServiceClient.getAudienceIds(command.getAuthorId());
            if (response != null && response.getData() != null) {
                recipientUserIds.addAll(response.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch audience IDs from user-service for author {}: {}", command.getAuthorId(), e.getMessage());
        }

        List<UserFeedItem> feedItems = new ArrayList<>();
        for (UUID userId : recipientUserIds) {
            if (userId != null && !userFeedItemRepository.existsByUserIdAndPostId(userId, command.getPostId())) {
                feedItems.add(UserFeedItem.builder()
                        .userId(userId)
                        .postId(command.getPostId())
                        .authorId(command.getAuthorId())
                        .createdAt(createdAt)
                        .build());
            }
        }

        if (!feedItems.isEmpty()) {
            userFeedItemRepository.saveAll(feedItems);
            log.info("Fanned out post {} to {} user feed items", command.getPostId(), feedItems.size());
        }
    }
}
