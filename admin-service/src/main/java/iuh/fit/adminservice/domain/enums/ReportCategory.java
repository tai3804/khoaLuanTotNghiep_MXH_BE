package iuh.fit.adminservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum ReportCategory {
    SPAM("Spam / Quảng cáo", "#1877f2", List.of("SPAM", "ADVERTISEMENT")),
    HATE_SPEECH("Nội dung thù ghét", "#f43f5e", List.of("HATE_SPEECH")),
    HARASSMENT("Quấy rối / Đe dọa", "#f59e0b", List.of("HARASSMENT", "BULLYING")),
    VIOLENCE_NUDITY("Bạo lực / Phản cảm", "#8b5cf6", List.of("VIOLENCE", "NUDITY", "SEXUAL_CONTENT")),
    FALSE_INFORMATION("Thông tin sai sự thật", "#06b6d4", List.of("FALSE_INFORMATION", "FAKE_NEWS")),
    OTHER("Khác", "#64748b", List.of("OTHER"));

    private final String displayName;
    private final String color;
    private final List<String> matchingReasons;

    public static ReportCategory fromReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return OTHER;
        }
        String normalized = rawReason.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(cat -> cat.getMatchingReasons().contains(normalized) || cat.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(OTHER);
    }
}
