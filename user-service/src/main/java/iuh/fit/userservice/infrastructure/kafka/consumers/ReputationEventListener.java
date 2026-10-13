package iuh.fit.userservice.infrastructure.kafka.consumers;

import iuh.fit.userservice.application.service.ReputationService;
import iuh.fit.userservice.presentation.dto.event.ReputationAdjustEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReputationEventListener {

    ReputationService reputationService;

    // Cấu hình consumer của user-service mặc định giải mã về UserRegisteredEvent, nên cần chỉ định kiểu riêng cho listener này
    @KafkaListener(
            topics = "user.reputation.adjust",
            groupId = "user-service-reputation-group",
            properties = "spring.json.value.default.type=iuh.fit.userservice.presentation.dto.event.ReputationAdjustEvent")
    public void handleReputationAdjust(ReputationAdjustEvent event) {
        log.info("Received reputation event: type={}, user={}, action={}, source={}",
                event.getType(), event.getUserId(), event.getAction(), event.getSourceId());
        try {
            if ("REVERT".equals(event.getType())) {
                reputationService.revertPenalties(event);
            } else if ("PENALTY".equals(event.getType())) {
                reputationService.applyPenalty(event);
            } else {
                log.warn("Ignore reputation event with unknown type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process reputation event {}: {}", event, e.getMessage(), e);
        }
    }
}
