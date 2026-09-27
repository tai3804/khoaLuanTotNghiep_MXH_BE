package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.aiservice.application.service.AiModerationService;
import iuh.fit.commonframework.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCreatedEventListener {

    private final AiModerationService aiModerationService;

    @KafkaListener(topics = "report.created", groupId = "ai-service-group")
    public void handleReportCreated(ReportCreatedEvent event) {
        log.info("Received ReportCreatedEvent for reportId: {}", event.getReportId());
        try {
            aiModerationService.evaluateReport(event);
        } catch (Exception e) {
            log.error("Failed to evaluate report {}", event.getReportId(), e);
        }
    }
}
