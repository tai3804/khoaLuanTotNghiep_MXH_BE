package iuh.fit.aiservice.infrastructure.gemini;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiModerationResult {
    private boolean isToxic;
    private double toxicityScore; // 0.0 to 1.0
    private String category; // HATE_SPEECH, HARASSMENT, SEXUAL, VIOLENCE, SPAM, PROFANITY, NONE
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String suggestedAction; // ALLOW, WARN_USER, AUTO_HIDE, DELETE_POST
    private String reason; // Tiếng Việt giải thích
    @Builder.Default
    private List<String> extractedKeywords = new ArrayList<>(); // Các từ lóng/từ nhạy cảm mới được trích xuất
    private boolean isFallback; // true nếu kết quả sinh ra từ fallback engine
}
