package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.application.dto.PostResponse;
import iuh.fit.aiservice.application.engine.FastSensitiveWordFilter;
import iuh.fit.aiservice.domain.entities.AiModerationLog;
import iuh.fit.aiservice.domain.repository.AiModerationLogRepository;
import iuh.fit.aiservice.infrastructure.event.AiNotificationProducer;
import iuh.fit.aiservice.infrastructure.feign.AdminFeignClient;
import iuh.fit.aiservice.infrastructure.feign.ModerationFeignClient;
import iuh.fit.aiservice.infrastructure.feign.PostFeignClient;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.aiservice.infrastructure.gemini.GeminiModerationResult;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.event.PostFlaggedEvent;
import iuh.fit.commonframework.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiModerationService {

    private final PostFeignClient postFeignClient;
    private final ModerationFeignClient moderationFeignClient;
    private final AdminFeignClient adminFeignClient;
    private final GeminiApiClient geminiApiClient;
    private final FastSensitiveWordFilter fastFilter;
    private final SelfLearningService selfLearningService;
    private final AiNotificationProducer aiNotificationProducer;
    private final iuh.fit.aiservice.infrastructure.event.PostModerationProducer postModerationProducer;
    private final AiModerationLogRepository moderationLogRepository;

    @Value("${ai.moderation.enabled:true}")
    private boolean moderationEnabled;

    @Value("${ai.moderation.thresholds.warn:0.40}")
    private double thresholdWarn;

    @Value("${ai.moderation.thresholds.hide:0.65}")
    private double thresholdHide;

    @Value("${ai.moderation.thresholds.delete:0.85}")
    private double thresholdDelete;

    /**
     * Xử lý báo cáo bài viết tự động khi nhận ReportCreatedEvent qua Kafka
     */
    @Transactional
    public void evaluateReport(ReportCreatedEvent event) {
        if (!moderationEnabled) {
            log.info("AI Moderation is disabled by configuration. Skipping report: {}", event.getReportId());
            return;
        }

        if (!"POST".equalsIgnoreCase(event.getTargetType())) {
            log.info("AI Moderation currently only evaluates POST targets. Skipping: {}", event.getTargetType());
            return;
        }

        try {
            ApiResponse<PostResponse> response = postFeignClient.getPost(event.getTargetId());
            if (response == null || response.getData() == null) {
                log.warn("Post not found or empty response for postId: {}", event.getTargetId());
                return;
            }

            PostResponse post = response.getData();
            String content = post.getContent();
            UUID authorId = post.getAuthorId();

            log.info("AI Evaluating reported post: {} [Author: {}]", event.getTargetId(), authorId);

            // 1. Phân tích nội dung qua Hybrid Engine (Tầng 1 + Tầng 2)
            GeminiModerationResult result = evaluateTextContent(content);

            // 2. Quyết định hành động thực thi dựa trên kết quả AI
            String action = result.getSuggestedAction();
            double score = result.getToxicityScore();

            if (score >= thresholdDelete || "DELETE_POST".equalsIgnoreCase(action)) {
                action = "DELETE_POST";
                log.warn("AI AUTO-RESOLVE [DELETE_POST]: Post {} has high toxicity score: {}", event.getTargetId(), score);

                // A. Gọi moderation-service giải quyết Report -> DELETE_POST
                ModerationFeignClient.ProcessReportRequest req = new ModerationFeignClient.ProcessReportRequest();
                req.setAction("DELETE_POST");
                req.setReason("AI_AUTO_MODERATION");
                req.setNote(String.format("AI phát hiện vi phạm nghiêm trọng (Score: %.2f - %s): %s",
                        score, result.getCategory(), result.getReason()));
                moderationFeignClient.processReport(event.getReportId(), req);

                // B. Gửi thông báo vi phạm tới tác giả
                if (authorId != null) {
                    aiNotificationProducer.sendWarningNotification(
                            authorId,
                            "Bài viết đã bị gỡ bỏ do vi phạm nghiêm trọng",
                            String.format("Bài viết của bạn đã bị hệ thống AI gỡ bỏ do vi phạm tiêu chuẩn cộng đồng (%s): %s",
                                    result.getCategory(), result.getReason()),
                            event.getTargetId().toString()
                    );
                }

            } else if (score >= thresholdHide || "AUTO_HIDE".equalsIgnoreCase(action)) {
                action = "AUTO_HIDE";
                log.warn("AI AUTO-RESOLVE [AUTO_HIDE]: Post {} has medium-high toxicity score: {}", event.getTargetId(), score);

                // A. Giải quyết Report -> HIDE
                ModerationFeignClient.ProcessReportRequest req = new ModerationFeignClient.ProcessReportRequest();
                req.setAction("HIDE_POST");
                req.setReason("AI_AUTO_MODERATION");
                req.setNote(String.format("AI tự động ẩn bài viết (Score: %.2f - %s): %s",
                        score, result.getCategory(), result.getReason()));
                moderationFeignClient.processReport(event.getReportId(), req);

                // B. Gửi cảnh báo giải thích cho tác giả
                if (authorId != null) {
                    aiNotificationProducer.sendWarningNotification(
                            authorId,
                            "Bài viết của bạn đã bị tạm ẩn",
                            String.format("Bài viết của bạn bị ẩn do chứa nội dung không phù hợp (%s): %s. Bạn có thể chỉnh sửa lại bài viết.",
                                    result.getCategory(), result.getReason()),
                            event.getTargetId().toString()
                    );
                }

            } else if (score >= thresholdWarn || "WARN_USER".equalsIgnoreCase(action)) {
                action = "WARN_USER";
                log.info("AI DISPATCH [WARN_USER]: Sending warning notification to author: {}", authorId);

                // Gửi cảnh báo nhắc nhở riêng tư
                if (authorId != null) {
                    aiNotificationProducer.sendWarningNotification(
                            authorId,
                            "Cảnh báo nội dung từ hệ thống AI",
                            String.format("Bài viết của bạn có dấu hiệu vi phạm chuẩn mực (%s): %s. Vui lòng kiểm tra lại.",
                                    result.getCategory(), result.getReason()),
                            event.getTargetId().toString()
                    );
                }

            } else {
                action = "ALLOW";
                log.info("AI Evaluation Result: Post {} is SAFE (Score: {}).", event.getTargetId(), score);
            }

            // 3. Tầng 3: Tự học các từ nhạy cảm mới được trích xuất
            if (result.getExtractedKeywords() != null && !result.getExtractedKeywords().isEmpty()) {
                selfLearningService.learnKeywords(
                        result.getExtractedKeywords(),
                        result.getCategory(),
                        result.getSeverity(),
                        true
                );

                // Đồng bộ từ điển sang admin-service nếu cần
                for (String word : result.getExtractedKeywords()) {
                    try {
                        adminFeignClient.learnSensitiveWord(word);
                    } catch (Exception ex) {
                        // ignore admin sync error
                    }
                }
            }

            // 4. Lưu vết nhật ký AI Moderation Log
            AiModerationLog logEntry = AiModerationLog.builder()
                    .targetType("POST")
                    .targetId(event.getTargetId())
                    .authorId(authorId)
                    .reportId(event.getReportId())
                    .contentSnippet(content != null && content.length() > 255 ? content.substring(0, 255) + "..." : content)
                    .toxicityScore(score)
                    .category(result.getCategory())
                    .severity(result.getSeverity())
                    .actionTaken(action)
                    .reason(result.getReason())
                    .extractedKeywords(result.getExtractedKeywords() != null ? String.join(", ", result.getExtractedKeywords()) : "")
                    .isFallback(result.isFallback())
                    .build();

            moderationLogRepository.save(logEntry);
            log.info("Successfully recorded AI Moderation Log for targetId: {}", event.getTargetId());

        } catch (Exception e) {
            log.error("Error during AI evaluation for report {}: {}", event.getReportId(), e.getMessage(), e);
        }
    }

    /**
     * Đánh giá văn bản trực tiếp (phục vụ REST API kiểm tra real-time)
     */
    public GeminiModerationResult evaluateTextContent(String content) {
        if (content == null || content.isBlank()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("Nội dung rỗng")
                    .extractedKeywords(new ArrayList<>())
                    .build();
        }

        // Ưu tiên Gemini AI phân tích toàn diện ngữ cảnh ngữ nghĩa tiếng Việt
        // Nếu API ngoại tuyến, GeminiApiClient tự động kích hoạt Fallback Engine chuẩn mực và giáo dục
        return geminiApiClient.evaluate(content);
    }

    /**
     * Đánh giá và kiểm duyệt hình ảnh đa phương thức (Multimodal Vision Moderation)
     */
    public GeminiModerationResult evaluateImageContent(String imageUrl, String caption) {
        return geminiApiClient.evaluateImageWithVision(imageUrl, caption);
    }

    private volatile long lastConfigCheck = 0;
    private volatile boolean cachedDynamicEnabled = true;

    private boolean isDynamicModerationEnabled() {
        if (!moderationEnabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastConfigCheck > 30000) {
            lastConfigCheck = now;
            try {
                Map<String, String> configs = adminFeignClient.getSystemConfigs();
                if (configs != null && configs.containsKey("ai_moderation_enabled")) {
                    cachedDynamicEnabled = !"false".equalsIgnoreCase(configs.get("ai_moderation_enabled"));
                }
            } catch (Exception e) {
                log.debug("Could not refresh system configs from admin-service: {}", e.getMessage());
            }
        }
        return cachedDynamicEnabled;
    }

    /**
     * Tự động kiểm duyệt bài viết mới tạo qua sự kiện Kafka 'post.created'.
     * <p>
     * Bài nghi vấn vi phạm sẽ được tạm ẩn và gắn cờ cho kiểm duyệt viên quyết định (gỡ hẳn hoặc khôi phục),
     * AI không tự gỡ vĩnh viễn bài viết.
     *
     * @return true nếu bài viết an toàn (hoặc kiểm duyệt AI đang tắt) và có thể tiếp tục tóm tắt;
     *         false nếu bài bị gắn cờ, đã được đánh giá trước đó hoặc không có nội dung.
     */
    @Transactional
    public boolean evaluateNewPost(UUID postId, UUID authorId, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        if (!isDynamicModerationEnabled()) {
            log.info("AI Moderation is disabled by System Admin Setting. Skipping auto-moderation for post: {}", postId);
            return true;
        }

        if (postId != null && moderationLogRepository.existsByTargetTypeAndTargetId("POST", postId)) {
            log.info("Post {} has already been evaluated by AI. Skipping duplicate evaluation.", postId);
            return false;
        }

        log.info("AI Auto-Evaluating newly created post: {} [Author: {}]", postId, authorId);
        try {
            GeminiModerationResult result = evaluateTextContent(content);

            String suggested = result.getSuggestedAction();
            double score = result.getToxicityScore();

            boolean flagged = score >= thresholdWarn
                    || result.isToxic()
                    || "AUTO_HIDE".equalsIgnoreCase(suggested)
                    || "WARN_USER".equalsIgnoreCase(suggested)
                    || "DELETE_POST".equalsIgnoreCase(suggested);

            String action;
            if (flagged) {
                action = "FLAGGED_FOR_REVIEW";
                log.warn("AI AUTO-MODERATION [FLAGGED]: Post {} flagged for moderator review (score: {}, category: {}, severity: {})",
                        postId, score, result.getCategory(), result.getSeverity());

                // A. Tạm ẩn bài viết trong lúc chờ kiểm duyệt viên xem xét
                postModerationProducer.publishPostModerated(postId, "HIDE_POST", "AI_AUTO_MODERATION: " + result.getReason());

                // B. Gắn cờ cho kiểm duyệt viên (admin-service đưa vào hàng chờ kiểm duyệt)
                postModerationProducer.publishPostFlagged(PostFlaggedEvent.builder()
                        .postId(postId)
                        .authorId(authorId)
                        .score(score)
                        .category(result.getCategory())
                        .severity(result.getSeverity())
                        .suggestedAction(suggested)
                        .reason(result.getReason())
                        .keywords(result.getExtractedKeywords() != null ? String.join(", ", result.getExtractedKeywords()) : "")
                        .contentSnippet(content.length() > 255 ? content.substring(0, 255) + "..." : content)
                        .fallback(result.isFallback())
                        .build());

                // C. Báo cho tác giả biết bài đang chờ kiểm duyệt
                if (authorId != null) {
                    aiNotificationProducer.sendWarningNotification(
                            authorId,
                            "Bài viết của bạn đang chờ kiểm duyệt",
                            String.format("Bài viết của bạn bị tạm ẩn vì có dấu hiệu vi phạm (%s): %s. Kiểm duyệt viên sẽ xem xét và phản hồi sớm. Bạn có thể gửi kháng nghị nếu cho rằng đây là nhầm lẫn.",
                                    result.getCategory(), result.getReason()),
                            postId.toString()
                    );
                }
            } else {
                action = "ALLOW";
                log.info("AI Auto-Moderation: Post {} is CLEAN (Score: {})", postId, score);
            }

            if (result.getExtractedKeywords() != null && !result.getExtractedKeywords().isEmpty()) {
                selfLearningService.learnKeywords(
                        result.getExtractedKeywords(),
                        result.getCategory(),
                        result.getSeverity(),
                        true
                );
            }

            AiModerationLog logEntry = AiModerationLog.builder()
                    .targetType("POST")
                    .targetId(postId)
                    .authorId(authorId)
                    .reportId(null)
                    .contentSnippet(content.length() > 255 ? content.substring(0, 255) + "..." : content)
                    .toxicityScore(score)
                    .category(result.getCategory())
                    .severity(result.getSeverity())
                    .actionTaken(action)
                    .reason(result.getReason())
                    .extractedKeywords(result.getExtractedKeywords() != null ? String.join(", ", result.getExtractedKeywords()) : "")
                    .isFallback(result.isFallback())
                    .build();

            moderationLogRepository.save(logEntry);
            log.info("Successfully recorded AI Moderation Log for new post: {}", postId);

            return !flagged;
        } catch (Exception e) {
            log.error("Error during auto AI evaluation for post {}: {}", postId, e.getMessage(), e);
            return false;
        }
    }
}
