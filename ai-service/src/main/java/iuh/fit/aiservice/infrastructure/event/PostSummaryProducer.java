package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.commonframework.event.PostSummarizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostSummaryProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPostSummarized(UUID postId, String summary, int contentLength) {
        if (postId == null || summary == null || summary.isBlank()) return;

        try {
            PostSummarizedEvent event = PostSummarizedEvent.builder()
                    .postId(postId)
                    .summary(summary)
                    .contentLength(contentLength)
                    .build();

            kafkaTemplate.send("post.summarized", event);
            log.info("Published PostSummarizedEvent to 'post.summarized' for postId: {} ({} chars -> {} chars)",
                    postId, contentLength, summary.length());
        } catch (Exception e) {
            log.error("Failed to publish PostSummarizedEvent for postId {}: {}", postId, e.getMessage(), e);
        }
    }
}
