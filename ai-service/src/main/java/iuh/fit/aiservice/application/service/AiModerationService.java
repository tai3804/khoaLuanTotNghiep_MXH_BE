package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.application.dto.PostResponse;
import iuh.fit.aiservice.infrastructure.feign.AdminFeignClient;
import iuh.fit.aiservice.infrastructure.feign.ModerationFeignClient;
import iuh.fit.aiservice.infrastructure.feign.PostFeignClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiModerationService {

    private final PostFeignClient postFeignClient;
    private final ModerationFeignClient moderationFeignClient;
    private final AdminFeignClient adminFeignClient;

    public void evaluateReport(ReportCreatedEvent event) {
        if (!"POST".equalsIgnoreCase(event.getTargetType())) {
            log.info("AI Moderation currently only supports POST target types. Ignored: {}", event.getTargetType());
            return;
        }

        try {
            ApiResponse<PostResponse> response = postFeignClient.getPost(event.getTargetId());
            if (response == null || response.getData() == null) {
                log.warn("Post not found or empty response for id: {}", event.getTargetId());
                return;
            }
            
            String content = response.getData().getContent();
            log.info("Evaluating post content: {}", content);

            // TODO: Replace with real Gemini/OpenAI API call. 
            // Dummy logic: If content contains "địt" or "ngu", we assume it's toxic.
            List<String> mockToxicWords = Arrays.asList("địt", "ngu", "chó", "lồn");
            boolean isToxic = false;
            String foundWord = "";

            if (content != null) {
                String lowerContent = content.toLowerCase();
                for (String word : mockToxicWords) {
                    if (lowerContent.contains(word)) {
                        isToxic = true;
                        foundWord = word;
                        break;
                    }
                }
            }

            if (isToxic) {
                log.info("AI determined post {} is toxic based on word '{}'. Auto-approving report.", event.getTargetId(), foundWord);
                
                // 1. Resolve Report -> DELETE_POST
                ModerationFeignClient.ProcessReportRequest processReq = new ModerationFeignClient.ProcessReportRequest();
                processReq.setAction("DELETE_POST");
                processReq.setReason("AI_AUTO_APPROVED");
                processReq.setNote("AI detected toxic word: " + foundWord);
                moderationFeignClient.processReport(event.getReportId(), processReq);

                // 2. Learn the sensitive word
                try {
                    adminFeignClient.learnSensitiveWord(foundWord);
                    log.info("Successfully learned new sensitive word: {}", foundWord);
                } catch (Exception e) {
                    log.error("Failed to push sensitive word to admin-service: {}", foundWord, e);
                }
            } else {
                log.info("AI determined post {} is SAFE. Skipping auto-moderation.", event.getTargetId());
            }

        } catch (Exception e) {
            log.error("Error during AI moderation for report {}: {}", event.getReportId(), e.getMessage());
        }
    }
}
