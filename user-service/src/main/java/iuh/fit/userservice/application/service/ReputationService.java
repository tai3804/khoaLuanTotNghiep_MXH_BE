package iuh.fit.userservice.application.service;

import iuh.fit.userservice.domain.entities.ReputationEvent;
import iuh.fit.userservice.domain.entities.UserReputation;
import iuh.fit.userservice.domain.enums.ReputationLevel;
import iuh.fit.userservice.domain.repository.ReputationEventRepository;
import iuh.fit.userservice.domain.repository.UserReputationRepository;
import iuh.fit.userservice.presentation.dto.event.ReputationAdjustEvent;
import iuh.fit.userservice.presentation.dto.response.ReputationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Điểm uy tín của người dùng: bắt đầu ở mức tối đa (100) và bị trừ khi nội dung của họ bị cảnh cáo, tạm ẩn hoặc gỡ.
 * - Mỗi bài viết chỉ bị trừ theo mức nặng nhất (cảnh cáo rồi ẩn rồi gỡ không cộng dồn mà chỉ trừ phần chênh lệch).
 * - Quyết định kiểm duyệt bị thu hồi (khôi phục, bỏ qua báo cáo, kháng nghị được chấp nhận) thì hoàn lại điểm đã trừ cho bài đó.
 * - Mỗi lần thay đổi đều gửi thông báo cho người dùng, kèm cảnh báo mạnh hơn khi họ rơi xuống bậc thấp hơn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    private static final String SOURCE_POST = "POST";

    private final UserReputationRepository reputationRepository;
    private final ReputationEventRepository eventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${reputation.initial-score:100}")
    private int initialScore;
    @Value("${reputation.penalty.warn:5}")
    private int warnPenalty;
    @Value("${reputation.penalty.auto-hide:5}")
    private int autoHidePenalty;
    @Value("${reputation.penalty.hide:10}")
    private int hidePenalty;
    @Value("${reputation.penalty.delete:20}")
    private int deletePenalty;

    @Transactional(readOnly = true)
    public ReputationResponse getReputation(UUID userId) {
        int score = reputationRepository.findByUserId(userId).map(UserReputation::getScore).orElse(initialScore);
        ReputationLevel level = ReputationLevel.of(score);
        ReputationLevel[] levels = ReputationLevel.values();
        Integer nextLevelScore = null;
        for (int i = 1; i < levels.length; i++) {
            if (levels[i] == level) {
                nextLevelScore = levels[i - 1].getMinScore();
            }
        }
        return ReputationResponse.builder()
                .userId(userId)
                .score(score)
                .maxScore(initialScore)
                .level(level.name())
                .levelLabel(level.getLabel())
                .nextLevelScore(nextLevelScore)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ReputationEvent> getHistory(UUID userId, Pageable pageable) {
        return eventRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void applyPenalty(ReputationAdjustEvent event) {
        int target = penaltyFor(event.getAction());
        if (event.getUserId() == null || target <= 0) {
            log.warn("Ignore reputation penalty with unknown action {} for user {}", event.getAction(), event.getUserId());
            return;
        }
        String sourceType = event.getSourceType() != null ? event.getSourceType() : SOURCE_POST;

        // Cùng một bài chỉ bị trừ theo mức nặng nhất: chỉ trừ phần chênh lệch so với những gì đã trừ
        int alreadyApplied = event.getSourceId() == null ? 0 : outstandingPoints(event.getUserId(), sourceType, event.getSourceId());
        int deduction = target - alreadyApplied;
        if (deduction <= 0) {
            log.info("Reputation penalty {} for user {} already covered by earlier penalties on {}", event.getAction(), event.getUserId(), event.getSourceId());
            return;
        }

        UserReputation reputation = getOrCreate(event.getUserId());
        int before = reputation.getScore();
        int after = Math.max(0, before - deduction);
        int actual = before - after;
        if (actual == 0) {
            log.info("User {} is already at the minimum reputation score", event.getUserId());
            return;
        }
        reputation.setScore(after);
        reputationRepository.save(reputation);
        eventRepository.save(ReputationEvent.builder()
                .userId(event.getUserId())
                .type(ReputationEvent.PENALTY)
                .action(event.getAction())
                .sourceType(sourceType)
                .sourceId(event.getSourceId())
                .points(actual)
                .scoreBefore(before)
                .scoreAfter(after)
                .reason(truncate(event.getReason()))
                .actorId(event.getActorId())
                .build());
        log.info("Reputation of user {} {} -> {} ({})", event.getUserId(), before, after, event.getAction());

        notifyPenalty(event, before, after, actual);
    }

    @Transactional
    public void revertPenalties(ReputationAdjustEvent event) {
        if (event.getUserId() == null || event.getSourceId() == null) {
            return;
        }
        String sourceType = event.getSourceType() != null ? event.getSourceType() : SOURCE_POST;
        List<ReputationEvent> outstanding = eventRepository
                .findByUserIdAndSourceTypeAndSourceIdAndTypeAndRevertedFalse(event.getUserId(), sourceType, event.getSourceId(), ReputationEvent.PENALTY);
        int total = outstanding.stream().mapToInt(ReputationEvent::getPoints).sum();
        if (total <= 0) {
            return;
        }
        outstanding.forEach(e -> e.setReverted(true));
        eventRepository.saveAll(outstanding);

        UserReputation reputation = getOrCreate(event.getUserId());
        int before = reputation.getScore();
        int after = Math.min(initialScore, before + total);
        int actual = after - before;
        reputation.setScore(after);
        reputationRepository.save(reputation);
        if (actual <= 0) {
            return;
        }
        eventRepository.save(ReputationEvent.builder()
                .userId(event.getUserId())
                .type(ReputationEvent.REFUND)
                .sourceType(sourceType)
                .sourceId(event.getSourceId())
                .points(actual)
                .scoreBefore(before)
                .scoreAfter(after)
                .reason(truncate(event.getReason()))
                .build());
        log.info("Reputation of user {} refunded {} -> {}", event.getUserId(), before, after);

        ReputationLevel level = ReputationLevel.of(after);
        sendNotification(event.getUserId(), null,
                "Điểm uy tín của bạn được hoàn lại",
                "Quyết định kiểm duyệt trước đó đã được thu hồi nên điểm uy tín được hoàn lại: "
                        + before + " → " + after + " (+" + actual + "), bậc " + level.getLabel() + ".",
                event.getSourceId());
    }

    private int penaltyFor(String action) {
        if (action == null) {
            return 0;
        }
        return switch (action) {
            case "WARN_USER" -> warnPenalty;
            case "AUTO_HIDE" -> autoHidePenalty;
            case "HIDE_POST" -> hidePenalty;
            case "DELETE_POST" -> deletePenalty;
            default -> 0;
        };
    }

    private int outstandingPoints(UUID userId, String sourceType, UUID sourceId) {
        return eventRepository
                .findByUserIdAndSourceTypeAndSourceIdAndTypeAndRevertedFalse(userId, sourceType, sourceId, ReputationEvent.PENALTY)
                .stream().mapToInt(ReputationEvent::getPoints).sum();
    }

    private UserReputation getOrCreate(UUID userId) {
        return reputationRepository.findByUserId(userId)
                .orElseGet(() -> reputationRepository.save(UserReputation.builder().userId(userId).score(initialScore).build()));
    }

    private void notifyPenalty(ReputationAdjustEvent event, int before, int after, int actual) {
        ReputationLevel oldLevel = ReputationLevel.of(before);
        ReputationLevel newLevel = ReputationLevel.of(after);
        boolean droppedLevel = newLevel != oldLevel;

        String cause = switch (String.valueOf(event.getAction())) {
            case "WARN_USER" -> "Bạn nhận được cảnh cáo từ kiểm duyệt viên";
            case "AUTO_HIDE" -> "Bài viết của bạn bị cộng đồng báo cáo nhiều lần nên đã được tạm ẩn";
            case "HIDE_POST" -> "Bài viết của bạn bị kiểm duyệt viên ẩn";
            case "DELETE_POST" -> "Bài viết của bạn bị kiểm duyệt viên gỡ bỏ";
            default -> "Tài khoản của bạn bị ghi nhận một vi phạm";
        };
        StringBuilder content = new StringBuilder(cause)
                .append(". Điểm uy tín: ").append(before).append(" → ").append(after)
                .append(" (-").append(actual).append("), bậc ").append(newLevel.getLabel()).append(".");
        if (droppedLevel) {
            content.append(" Bạn vừa xuống bậc \"").append(newLevel.getLabel()).append("\".");
            switch (newLevel) {
                case CAUTION -> content.append(" Vui lòng chú ý hơn tới nội dung bạn đăng.");
                case WARNING -> content.append(" Hãy tuân thủ tiêu chuẩn cộng đồng để tránh bị hạn chế tài khoản.");
                case CRITICAL -> content.append(" Tài khoản của bạn có nguy cơ bị hạn chế hoặc khoá nếu tiếp tục vi phạm.");
                default -> { }
            }
        }
        String title = droppedLevel
                ? "Cảnh báo: điểm uy tín của bạn xuống bậc " + newLevel.getLabel()
                : "Điểm uy tín của bạn bị trừ";
        sendNotification(event.getUserId(), event.getActorId(), title, content.toString(), event.getSourceId());
    }

    private void sendNotification(UUID userId, UUID actorId, String title, String content, UUID sourceId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId", userId.toString());
            payload.put("actorId", actorId != null ? actorId.toString() : null);
            payload.put("type", "SYSTEM");
            payload.put("title", title);
            payload.put("content", content);
            payload.put("targetId", sourceId != null ? sourceId.toString() : "");
            payload.put("targetUrl", "/profile");
            payload.put("avatarUrl", "");
            kafkaTemplate.send("notification.in-app.send", payload);
        } catch (Exception e) {
            log.warn("Failed to publish reputation notification for user {}: {}", userId, e.getMessage());
        }
    }

    private static String truncate(String text) {
        return text != null && text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
