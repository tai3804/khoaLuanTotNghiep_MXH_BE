package iuh.fit.userservice.infrastructure.scheduler;

import iuh.fit.userservice.application.service.ReputationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chạy định kỳ: hồi điểm uy tín (+1 mỗi ngày không vi phạm), mở khóa tài khoản hết hạn, thử lại các lần khóa bị lỗi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReputationScheduler {

    private final ReputationService reputationService;

    @Scheduled(
            fixedDelayString = "${reputation.maintenance.interval-ms:3600000}",
            initialDelayString = "${reputation.maintenance.initial-delay-ms:120000}")
    public void run() {
        try {
            reputationService.runMaintenance();
        } catch (Exception e) {
            log.error("Reputation maintenance failed: {}", e.getMessage(), e);
        }
    }
}
