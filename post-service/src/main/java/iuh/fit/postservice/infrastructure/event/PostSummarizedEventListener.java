package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostSummarizedEvent;
import iuh.fit.postservice.application.features.post.commands.apply_summary.ApplyPostSummaryCommand;
import iuh.fit.postservice.application.features.post.commands.apply_summary.ApplyPostSummaryCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Nhận bản tóm tắt bài viết dài do ai-service tạo (topic "post.summarized") và lưu cùng bài viết.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostSummarizedEventListener {

    private final ApplyPostSummaryCommandHandler applyPostSummaryCommandHandler;

    @KafkaListener(topics = "post.summarized", groupId = "post-summary-group", properties = "auto.offset.reset=earliest")
    public void handlePostSummarized(PostSummarizedEvent event) {
        if (event == null || event.getPostId() == null || event.getSummary() == null || event.getSummary().isBlank()) {
            return;
        }

        log.info("Received PostSummarizedEvent for postId: {}", event.getPostId());
        try {
            applyPostSummaryCommandHandler.handle(ApplyPostSummaryCommand.builder()
                    .postId(event.getPostId())
                    .summary(event.getSummary())
                    .contentLength(event.getContentLength())
                    .build());
        } catch (Exception e) {
            log.error("Failed to apply AI summary for post {}: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
