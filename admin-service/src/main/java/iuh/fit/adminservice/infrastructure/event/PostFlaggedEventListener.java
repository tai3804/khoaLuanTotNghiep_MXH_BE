package iuh.fit.adminservice.infrastructure.event;

import iuh.fit.adminservice.application.features.report.commands.create_ai_flag.CreateAiFlagReportCommand;
import iuh.fit.adminservice.application.features.report.commands.create_ai_flag.CreateAiFlagReportCommandHandler;
import iuh.fit.commonframework.event.PostFlaggedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Nhận bài viết bị AI gắn cờ (topic "post.flagged") từ ai-service và đưa vào hàng chờ của kiểm duyệt viên.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostFlaggedEventListener {

    private final CreateAiFlagReportCommandHandler createAiFlagReportCommandHandler;

    @KafkaListener(topics = "post.flagged", groupId = "admin-post-flagged-group", properties = "auto.offset.reset=earliest")
    public void handlePostFlagged(PostFlaggedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }

        log.info("Received PostFlaggedEvent in admin-service for postId: {}, score: {}", event.getPostId(), event.getScore());
        try {
            createAiFlagReportCommandHandler.handle(CreateAiFlagReportCommand.builder()
                    .postId(event.getPostId())
                    .authorId(event.getAuthorId())
                    .score(event.getScore())
                    .category(event.getCategory())
                    .severity(event.getSeverity())
                    .reason(event.getReason())
                    .build());
        } catch (Exception e) {
            log.error("Failed to create AI flag report for post {}: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
