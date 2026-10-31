package iuh.fit.userservice.application.service;

import iuh.fit.commonframework.application.exception.BaseError;
import iuh.fit.commonframework.application.exception.BusinessException;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Điểm uy tín của người dùng: bắt đầu ở mức tối đa (100) và bị trừ khi nội dung của họ bị cảnh cáo, tạm ẩn hoặc gỡ.
 * - Mỗi bài viết chỉ bị trừ theo mức nặng nhất (cảnh cáo rồi ẩn rồi gỡ không cộng dồn mà chỉ trừ phần chênh lệch).
 * - Quyết định kiểm duyệt bị thu hồi (khôi phục, bỏ qua báo cáo, kháng nghị được chấp nhận) thì hoàn lại điểm đã trừ cho bài đó.
 * - Mỗi bậc uy tín có quyền hạn/mức phạt riêng (xem {@link ReputationPolicyProvider}); dưới ngưỡng thấp nhất thì khóa tài khoản
 *   {@code reputation.lock.days} ngày, hết hạn khóa điểm được đặt lại {@code reputation.lock.restore-score}.
 *   Khóa KHÔNG chặn đăng nhập: người dùng vẫn vào được nhưng FE hiện màn hình "Tài khoản của bạn đã bị khóa" và BE từ chối
 *   các thao tác ghi (đăng bài, bình luận, thả cảm xúc...) bằng mã lỗi ACCOUNT_LOCKED.
 * - Mỗi ngày không bị trừ điểm được tự hồi {@code reputation.recovery.points-per-day} điểm (công việc định kỳ {@link #runMaintenance()}).
 * - Mỗi lần thay đổi đều gửi thông báo cho người dùng, kèm cảnh báo mạnh hơn khi họ rơi xuống bậc thấp hơn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    private static final String SOURCE_POST = "POST";
    /** Mã lỗi (trường code của phản hồi) khi tài khoản đang bị khóa; FE dùng mã này để hiện màn hình "Tài khoản đã bị khóa". */
    public static final int ACCOUNT_LOCKED_CODE = 40320;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final UserReputationRepository reputationRepository;
    private final ReputationEventRepository eventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ReputationPolicyProvider policies;

    @Value("${reputation.initial-score:100}")
    private int initialScore;
    @Value("${reputation.penalty.warn:5}")
    private int warnPenalty;
    @Value("${reputation.penalty.auto-hide:5}")
    private int autoHidePenalty;
    @Value("${reputation.penalty.hide:10}")
    private int hidePenalty;
    @Value("${reputation.penalty.delete:10}")
    private int deletePenalty;
    @Value("${reputation.recovery.points-per-day:1}")
    private int recoveryPerDay;
    @Value("${reputation.lock.days:30}")
    private int lockDays;
    @Value("${reputation.lock.restore-score:30}")
    private int lockRestoreScore;

    @Transactional(readOnly = true)
    public ReputationResponse getReputation(UUID userId) {
        UserReputation row = reputationRepository.findByUserId(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        int score = row != null ? effectiveScore(row, now) : initialScore;
        LocalDateTime lockedUntil = row != null && row.getLockedUntil() != null && row.getLockedUntil().isAfter(now)
                ? row.getLockedUntil() : null;
        ReputationLevel level = ReputationLevel.of(score);
        ReputationLevel[] levels = ReputationLevel.values();
        Integer nextLevelScore = null;
        for (int i = 1; i < levels.length; i++) {
            if (levels[i] == level) {
                nextLevelScore = levels[i - 1].getMinScore();
            }
        }
        LocalDateTime nextRecoveryAt = null;
        if (row != null && row.getLockedUntil() == null && score < initialScore) {
            nextRecoveryAt = anchorOf(row).plusDays(1);
        }
        return ReputationResponse.builder()
                .userId(userId)
                .score(score)
                .maxScore(initialScore)
                .level(level.name())
                .levelLabel(level.getLabel())
                .nextLevelScore(nextLevelScore)
                .policy(policies.of(level))
                .tiers(policies.all())
                .recoveryPerDay(recoveryPerDay)
                .nextRecoveryAt(nextRecoveryAt)
                .lockedUntil(lockedUntil)
                .build();
    }

    /** Chính sách quyền hạn hiện tại của người dùng (chưa có bản ghi điểm = điểm tối đa, không hạn chế). */
    @Transactional(readOnly = true)
    public ReputationPolicy getPolicy(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        int score = reputationRepository.findByUserId(userId).map(r -> effectiveScore(r, now)).orElse(initialScore);
        return policies.of(score);
    }

    /** Hệ số xếp hạng bảng tin của những người bị giảm độ ưu tiên (người không có trong kết quả = 1). */
    @Transactional(readOnly = true)
    public Map<UUID, Double> getReducedFeedWeights(Collection<UUID> userIds) {
        Map<UUID, Double> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (UserReputation row : reputationRepository.findByUserIdIn(userIds)) {
            double weight = policies.of(effectiveScore(row, LocalDateTime.now())).feedWeight();
            if (weight < 1.0) {
                result.put(row.getUserId(), weight);
            }
        }
        return result;
    }

    /** Chặn thao tác bị cấm ở bậc hiện tại của người dùng (vd tạo nhóm). */
    @Transactional(readOnly = true)
    public void assertCanCreateGroup(UUID userId) {
        ReputationPolicy policy = getPolicy(userId);
        if (policy.accountLocked()) {
            throw new BusinessException(restricted(ACCOUNT_LOCKED_CODE,
                    "Tài khoản của bạn đang bị khóa do điểm uy tín quá thấp nên chưa thể sử dụng chức năng này."));
        }
        if (!policy.canCreateGroup()) {
            throw new BusinessException(restricted(403, "Điểm uy tín của bạn đang ở mức " + policy.levelLabel()
                    + " nên chưa thể tạo nhóm. Điểm sẽ tự hồi phục " + recoveryPerDay + " điểm mỗi ngày không vi phạm."));
        }
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
        reputation.setRecoveryAnchorAt(LocalDateTime.now()); // bị trừ điểm thì bắt đầu tính lại 1 ngày để hồi điểm
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

        if (ReputationLevel.of(after) == ReputationLevel.LOCKED) {
            lockAccount(reputation, before, after, event);
        } else {
            notifyPenalty(event, before, after, actual);
        }
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

        // Khóa tài khoản là do khoản phạt vừa được thu hồi: điểm đã trở lại trên ngưỡng khóa thì mở khóa ngay
        if (reputation.getLockedUntil() != null && level != ReputationLevel.LOCKED) {
            releaseLock(reputation, null, "Điểm uy tín được hoàn lại nên tài khoản được mở khóa.");
        }
    }

    // ---------------------------------------------------------------- công việc định kỳ

    /**
     * Chạy định kỳ (mỗi giờ) và có thể gọi tay qua API nội bộ: mở khóa tài khoản hết hạn và cộng điểm hồi phục
     * cho người đã đủ một ngày không bị trừ điểm.
     */
    public Map<String, Integer> runMaintenance() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("unlocked", releaseExpiredLocks());
        result.put("recovered", applyDailyRecovery());
        if (result.values().stream().anyMatch(v -> v > 0)) {
            log.info("Reputation maintenance: {}", result);
        }
        return result;
    }

    private int releaseExpiredLocks() {
        int count = 0;
        for (UserReputation row : reputationRepository.findExpiredLocks(LocalDateTime.now())) {
            try {
                releaseLock(row, lockRestoreScore, "Hết thời gian khóa " + lockDays + " ngày.");
                count++;
            } catch (Exception e) {
                log.error("Failed to unlock user {}: {}", row.getUserId(), e.getMessage(), e);
            }
        }
        return count;
    }

    private int applyDailyRecovery() {
        if (recoveryPerDay <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (UserReputation row : reputationRepository.findRecoverable(initialScore, now.minusDays(1))) {
            try {
                LocalDateTime anchor = anchorOf(row);
                long days = Duration.between(anchor, now).toDays(); // bù cả những ngày hệ thống tắt
                if (days <= 0) {
                    continue;
                }
                int before = row.getScore();
                int after = (int) Math.min(initialScore, before + days * recoveryPerDay);
                row.setScore(after);
                row.setRecoveryAnchorAt(anchor.plusDays(days));
                reputationRepository.save(row);
                eventRepository.save(ReputationEvent.builder()
                        .userId(row.getUserId())
                        .type(ReputationEvent.RECOVERY)
                        .points(after - before)
                        .scoreBefore(before)
                        .scoreAfter(after)
                        .reason("Tự hồi phục " + recoveryPerDay + " điểm mỗi ngày không vi phạm")
                        .build());
                count++;

                ReputationLevel oldLevel = ReputationLevel.of(before);
                ReputationLevel newLevel = ReputationLevel.of(after);
                if (newLevel != oldLevel) {
                    sendNotification(row.getUserId(), null,
                            "Điểm uy tín của bạn đã lên bậc " + newLevel.getLabel(),
                            "Nhờ không vi phạm, điểm uy tín đã hồi phục " + before + " → " + after + ". Bậc hiện tại: " + newLevel.getLabel()
                                    + ". " + restrictionSummary(newLevel),
                            null);
                }
            } catch (Exception e) {
                log.error("Failed to recover reputation for user {}: {}", row.getUserId(), e.getMessage(), e);
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- khóa / mở khóa

    /**
     * Khóa tài khoản: chỉ ghi trạng thái ở user-service. Người dùng vẫn đăng nhập được nhưng FE hiện màn hình "Tài khoản
     * của bạn đã bị khóa" (đọc từ lockedUntil) và post-service từ chối các thao tác ghi cho tới khi hết hạn khóa.
     */
    private void lockAccount(UserReputation reputation, int before, int after, ReputationAdjustEvent trigger) {
        LocalDateTime until = LocalDateTime.now().plusDays(lockDays);
        reputation.setLockedUntil(until);
        reputationRepository.save(reputation);
        eventRepository.save(ReputationEvent.builder()
                .userId(reputation.getUserId())
                .type(ReputationEvent.LOCK)
                .action(trigger.getAction())
                .sourceType(trigger.getSourceType())
                .sourceId(trigger.getSourceId())
                .points(0)
                .scoreBefore(after)
                .scoreAfter(after)
                .reason("Điểm uy tín xuống " + after + " (dưới " + ReputationLevel.VERY_WEAK.getMinScore()
                        + "): khóa tài khoản " + lockDays + " ngày, đến " + until.format(WHEN))
                .actorId(trigger.getActorId())
                .build());
        log.warn("Locking account of user {} for {} days (reputation {} -> {})", reputation.getUserId(), lockDays, before, after);

        sendNotification(reputation.getUserId(), trigger.getActorId(),
                "Tài khoản của bạn bị khóa " + lockDays + " ngày",
                "Điểm uy tín của bạn đã xuống " + after + " (dưới " + ReputationLevel.VERY_WEAK.getMinScore()
                        + ") do vi phạm tiêu chuẩn cộng đồng nên tài khoản bị khóa đến " + until.format(WHEN)
                        + ". Trong thời gian này bạn không thể sử dụng các chức năng của mạng xã hội. Sau đó tài khoản được mở lại với "
                        + lockRestoreScore + " điểm uy tín.",
                trigger.getSourceId());
    }

    /**
     * Mở khóa tài khoản (hết hạn khóa hoặc khoản phạt gây ra khóa đã được thu hồi).
     *
     * @param restoreScore điểm đặt lại sau khi mở khóa (null = giữ nguyên điểm hiện tại)
     */
    private void releaseLock(UserReputation reputation, Integer restoreScore, String reason) {
        int before = reputation.getScore();
        int after = restoreScore != null ? Math.max(before, restoreScore) : before;
        reputation.setScore(after);
        reputation.setLockedUntil(null);
        reputation.setRecoveryAnchorAt(LocalDateTime.now());
        reputationRepository.save(reputation);
        eventRepository.save(ReputationEvent.builder()
                .userId(reputation.getUserId())
                .type(ReputationEvent.UNLOCK)
                .points(after - before)
                .scoreBefore(before)
                .scoreAfter(after)
                .reason(truncate(reason))
                .build());
        ReputationLevel level = ReputationLevel.of(after);
        sendNotification(reputation.getUserId(), null,
                "Tài khoản của bạn đã được mở khóa",
                reason + " Điểm uy tín hiện tại: " + after + ", bậc " + level.getLabel() + ". " + restrictionSummary(level),
                null);
        log.info("Unlocked account of user {} (score {} -> {})", reputation.getUserId(), before, after);
    }

    // ---------------------------------------------------------------- tiện ích

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

    /**
     * Điểm đang có hiệu lực: khóa đã hết hạn nhưng công việc định kỳ chưa kịp mở khóa thì coi như đã mở (điểm đặt lại),
     * để FE, post-service và user-service nhìn thấy cùng một trạng thái.
     */
    private int effectiveScore(UserReputation row, LocalDateTime now) {
        if (row.getLockedUntil() != null && !row.getLockedUntil().isAfter(now)) {
            return Math.max(row.getScore(), lockRestoreScore);
        }
        return row.getScore();
    }

    private static LocalDateTime anchorOf(UserReputation row) {
        if (row.getRecoveryAnchorAt() != null) {
            return row.getRecoveryAnchorAt();
        }
        return row.getUpdatedAt() != null ? row.getUpdatedAt() : LocalDateTime.now();
    }

    private String restrictionSummary(ReputationLevel level) {
        List<String> lines = policies.of(level).restrictions();
        return lines.isEmpty() ? "Bạn không bị hạn chế quyền nào." : "Hạn chế hiện tại: " + String.join("; ", lines) + ".";
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
            content.append(" Bạn vừa xuống bậc \"").append(newLevel.getLabel()).append("\". ")
                    .append(restrictionSummary(newLevel));
            if (newLevel == ReputationLevel.VERY_WEAK) {
                content.append(" Nếu điểm xuống dưới ").append(ReputationLevel.VERY_WEAK.getMinScore())
                        .append(", tài khoản sẽ bị khóa ").append(lockDays).append(" ngày.");
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

    private static BaseError restricted(int code, String message) {
        return new BaseError() {
            @Override
            public int getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public int getStatusCode() {
                return 403;
            }
        };
    }

    private static String truncate(String text) {
        return text != null && text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
