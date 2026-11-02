package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.aiservice.application.service.AiModerationService;
import iuh.fit.aiservice.application.service.PostSummaryService;
import iuh.fit.aiservice.infrastructure.feign.PostFeignClient;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.commonframework.event.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedEventListener {

    private final AiModerationService aiModerationService;
    private final PostSummaryService postSummaryService;
    private final GeminiApiClient geminiApiClient;
    private final PostFeignClient postFeignClient;

    @KafkaListener(topics = "post.created", groupId = "ai-service-post-group")
    public void handlePostCreated(PostCreatedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }

        log.info("Received PostCreatedEvent in ai-service for postId: {}, authorId: {}", event.getPostId(), event.getAuthorId());
        try {
            // 1. Kiểm duyệt: bài nghi vấn bị tạm ẩn và gắn cờ cho kiểm duyệt viên
            boolean safe = aiModerationService.evaluateNewPost(event.getPostId(), event.getAuthorId(), event.getContent());

            // 2. Chỉ bài an toàn mới được vector hóa và tóm tắt
            if (safe) {
                // Tự động tạo vector embedding 768 chiều phục vụ tìm kiếm ngữ nghĩa
                if (event.getContent() != null && !event.getContent().isBlank()) {
                    try {
                        List<Float> embedding = geminiApiClient.generateEmbedding(event.getContent());
                        if (embedding != null && !embedding.isEmpty()) {
                            postFeignClient.savePostEmbedding(event.getPostId(), embedding);
                            log.info("Automatically generated and stored 768-dim embedding for new post {}", event.getPostId());
                        }
                    } catch (Exception ex) {
                        log.warn("Could not generate/save embedding for post {}: {}", event.getPostId(), ex.getMessage());
                    }
                }

                // Tóm tắt bài viết dài
                postSummaryService.summarizeAndPublish(event.getPostId(), event.getContent());
            }
        } catch (Exception e) {
            log.error("Failed to process new post {} in ai-service: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
