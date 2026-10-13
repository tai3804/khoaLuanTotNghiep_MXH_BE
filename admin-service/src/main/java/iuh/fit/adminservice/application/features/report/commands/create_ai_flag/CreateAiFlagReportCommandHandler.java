package iuh.fit.adminservice.application.features.report.commands.create_ai_flag;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportReason;
import iuh.fit.adminservice.domain.enums.ReportSource;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Đưa bài viết bị AI gắn cờ vào hàng chờ của kiểm duyệt viên dưới dạng một báo cáo có nguồn "AI".
 * Báo cáo ở trạng thái REVIEWING nên xuất hiện ở tab "AI gắn cờ nghi vấn" của trang kiểm duyệt.
 * Cố ý KHÔNG phát ReportCreatedEvent: ai-service không cần tự đánh giá lại báo cáo do chính nó tạo ra,
 * và quyết định cuối cùng thuộc về kiểm duyệt viên.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateAiFlagReportCommandHandler {

    /** Định danh "người báo cáo" dành cho hệ thống AI. */
    public static final UUID AI_REPORTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    ReportRepository reportRepository;

    @Transactional
    public void handle(CreateAiFlagReportCommand command) {
        boolean alreadyFlagged = reportRepository.existsByTargetTypeAndTargetIdAndSourceAndStatusIn(
                TargetType.POST, command.getPostId(), ReportSource.AI,
                List.of(ReportStatus.PENDING, ReportStatus.REVIEWING));

        if (alreadyFlagged) {
            log.info("Post {} already has an open AI flag. Skipping duplicate.", command.getPostId());
            return;
        }

        String reason = command.getReason() != null ? command.getReason().trim() : "";

        Report report = Report.builder()
                .reporterId(AI_REPORTER_ID)
                .targetType(TargetType.POST)
                .targetId(command.getPostId())
                .reason(mapReason(command.getCategory()))
                .description(truncate(reason))
                .status(ReportStatus.REVIEWING)
                .source(ReportSource.AI)
                .aiScore(command.getScore())
                .aiCategory(command.getCategory())
                .aiSeverity(command.getSeverity())
                .aiReason(reason)
                .build();

        report = reportRepository.save(report);
        log.info("AI flagged post {} for moderator review (reportId: {}, score: {}, category: {})",
                command.getPostId(), report.getId(), command.getScore(), command.getCategory());
    }

    static ReportReason mapReason(String category) {
        if (category == null) {
            return ReportReason.OTHER;
        }
        return switch (category.trim().toUpperCase()) {
            case "HATE_SPEECH" -> ReportReason.HATE_SPEECH;
            case "HARASSMENT" -> ReportReason.HARASSMENT;
            case "VIOLENCE" -> ReportReason.VIOLENCE;
            case "SPAM_SCAM", "SPAM" -> ReportReason.SPAM;
            case "SEXUAL", "PROFANITY" -> ReportReason.INAPPROPRIATE_CONTENT;
            default -> ReportReason.OTHER;
        };
    }

    private static String truncate(String text) {
        return text.length() > MAX_DESCRIPTION_LENGTH ? text.substring(0, MAX_DESCRIPTION_LENGTH) : text;
    }
}
