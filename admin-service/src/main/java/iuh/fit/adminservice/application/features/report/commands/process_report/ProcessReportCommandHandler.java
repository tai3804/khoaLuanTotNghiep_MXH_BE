package iuh.fit.adminservice.application.features.report.commands.process_report;

import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.event.ModerationEventPublisher;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessReportCommandHandler {

    /** moderatorId dùng cho các quyết định tự động của hệ thống (vd: tự ẩn khi vượt ngưỡng báo cáo). */
    static final UUID SYSTEM_MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    ReportRepository reportRepository;
    ModerationLogRepository moderationLogRepository;
    ModerationEventPublisher moderationEventPublisher;

    @Transactional
    public void handle(ProcessReportCommand command) {
        // Hai action này chỉ do luồng duyệt kháng nghị của bài viết ghi vào nhật ký, không áp dụng cho báo cáo
        if (command.getAction() == ModerationAction.APPEAL_ACCEPTED || command.getAction() == ModerationAction.APPEAL_REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Report report = reportRepository.findById(command.getReportId())
                .orElseThrow(() -> new BusinessException(AdminServiceErrorCode.REPORT_NOT_FOUND));

        if (command.getAction() == ModerationAction.DISMISS) {
            report.setStatus(ReportStatus.DISMISSED);
            markResolved(report, command);
        } else if (command.getAction() == ModerationAction.RESTORE_POST) {
            report.setStatus(ReportStatus.PENDING);
            report.setResolvedAt(null);
            report.setResolvedBy(null);
        } else {
            report.setStatus(ReportStatus.RESOLVED);
            markResolved(report, command);
        }
        reportRepository.save(report);

        // Record moderation log
        ModerationLog log = ModerationLog.builder()
                .moderatorId(command.getModeratorId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .action(command.getAction())
                .reason(report.getReason().name())
                .note(command.getNote() != null ? command.getNote() : "Hoàn tác quyết định")
                .reportId(report.getId())
                .build();
        moderationLogRepository.save(log);

        if (report.getTargetType() == TargetType.POST) {
            if (command.getAction() == ModerationAction.DELETE_POST || command.getAction() == ModerationAction.HIDE_POST) {
                moderationEventPublisher.publishPostModerated(
                        report.getTargetId(),
                        command.getAction().name(),
                        report.getReason().name(),
                        command.getModeratorId()
                );
            } else if (command.getAction() == ModerationAction.RESTORE_POST) {
                publishRestore(report, command);
            } else if (command.getAction() == ModerationAction.DISMISS && shouldRestoreAfterDismiss(report)) {
                publishRestore(report, command);
            } else if (command.getAction() == ModerationAction.WARN_USER) {
                String note = command.getNote() != null && !command.getNote().isBlank() ? command.getNote().trim() : null;
                moderationEventPublisher.publishPostWarned(report.getTargetId(), note, command.getModeratorId());
            }
        }
        if (command.getAction() == ModerationAction.DELETE_COMMENT && report.getTargetType() == TargetType.COMMENT) {
            moderationEventPublisher.publishCommentModerated(
                    report.getTargetId(), command.getAction().name(), report.getReason().name(), command.getModeratorId());
        }
    }

    private void markResolved(Report report, ProcessReportCommand command) {
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedBy(command.getModeratorId());
    }

    private void publishRestore(Report report, ProcessReportCommand command) {
        moderationEventPublisher.publishPostModerated(
                report.getTargetId(),
                ModerationAction.RESTORE_POST.name(),
                report.getReason().name(),
                command.getModeratorId()
        );
    }

    /**
     * Bỏ qua một báo cáo chỉ nên hiện lại bài khi không còn lý do nào khác để bài bị ẩn:
     * - kiểm duyệt viên đã ẩn/gỡ bài (qua báo cáo khác) và chưa ai khôi phục -> giữ nguyên quyết định đó;
     * - vẫn còn báo cáo khác đang chờ xử lý -> chờ đến báo cáo cuối cùng.
     * Việc ẩn tự động của hệ thống/AI không phải quyết định của kiểm duyệt viên nên không chặn khôi phục.
     */
    private boolean shouldRestoreAfterDismiss(Report report) {
        Optional<ModerationLog> lastDecision = moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDesc(
                        TargetType.POST, report.getTargetId(),
                        List.of(ModerationAction.HIDE_POST, ModerationAction.DELETE_POST, ModerationAction.RESTORE_POST,
                                ModerationAction.APPEAL_ACCEPTED, ModerationAction.APPEAL_REJECTED));
        // Khôi phục (hoàn tác hoặc chấp nhận kháng nghị) thì không còn quyết định ẩn/gỡ nào đang có hiệu lực
        boolean confirmedByModerator = lastDecision.isPresent()
                && lastDecision.get().getAction() != ModerationAction.RESTORE_POST
                && lastDecision.get().getAction() != ModerationAction.APPEAL_ACCEPTED
                && !SYSTEM_MODERATOR_ID.equals(lastDecision.get().getModeratorId());
        if (confirmedByModerator) {
            return false;
        }
        return !reportRepository.existsByTargetTypeAndTargetIdAndStatusInAndIdNot(
                TargetType.POST, report.getTargetId(),
                List.of(ReportStatus.PENDING, ReportStatus.REVIEWING), report.getId());
    }
}
