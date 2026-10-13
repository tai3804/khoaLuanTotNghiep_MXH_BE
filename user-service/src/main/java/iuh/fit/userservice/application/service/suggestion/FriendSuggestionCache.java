package iuh.fit.userservice.application.service.suggestion;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.userservice.application.service.suggestion.FriendSuggestionEngine.RankedCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache kết quả xếp hạng gợi ý kết bạn trong Redis. Tính điểm khá tốn kém (quét quan hệ của toàn bộ bạn bè và nhóm)
 * nên chỉ tính lại sau 3 phút hoặc ngay khi quan hệ của người đó thay đổi (gửi/nhận/chấp nhận/từ chối lời mời,
 * huỷ kết bạn, chặn...). Redis lỗi thì bỏ qua cache, không ảnh hưởng chức năng.
 *
 * Mỗi phần tử lưu dạng chuỗi "userId|điểm|số bạn chung|lý do1;lý do2" để không phụ thuộc kiểu JSON tuỳ biến.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendSuggestionCache {

    private static final String PREFIX = "user-service:friend-suggestions:";
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final String REASON_SEPARATOR = ";";

    private final RedisCacheService redisCacheService;

    public Optional<List<RankedCandidate>> get(UUID viewerId) {
        try {
            Object cached = redisCacheService.get(PREFIX + viewerId);
            if (!(cached instanceof List<?> rows)) return Optional.empty();
            List<RankedCandidate> result = new ArrayList<>(rows.size());
            for (Object row : rows) {
                String[] parts = String.valueOf(row).split("\\|", 4);
                if (parts.length < 3) return Optional.empty();
                List<String> reasons = parts.length > 3 && !parts[3].isEmpty()
                        ? Arrays.asList(parts[3].split(REASON_SEPARATOR)) : List.of();
                result.add(new RankedCandidate(UUID.fromString(parts[0]), Double.parseDouble(parts[1]),
                        Long.parseLong(parts[2]), reasons));
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.debug("Friend suggestion cache read failed for {}: {}", viewerId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID viewerId, List<RankedCandidate> ranked) {
        try {
            List<String> rows = new ArrayList<>(ranked.size());
            for (RankedCandidate c : ranked) {
                String reasons = c.reasons().stream()
                        .map(r -> r.replace(REASON_SEPARATOR, ",").replace("|", "/"))
                        .collect(java.util.stream.Collectors.joining(REASON_SEPARATOR));
                rows.add(c.userId() + "|" + c.score() + "|" + c.mutualCount() + "|" + reasons);
            }
            redisCacheService.set(PREFIX + viewerId, rows, TTL);
        } catch (Exception e) {
            log.debug("Friend suggestion cache write failed for {}: {}", viewerId, e.getMessage());
        }
    }

    /** Xoá cache của các người dùng có quan hệ vừa thay đổi. */
    public void evict(UUID... userIds) {
        for (UUID id : userIds) {
            if (id == null) continue;
            try {
                redisCacheService.delete(PREFIX + id);
            } catch (Exception e) {
                log.debug("Friend suggestion cache evict failed for {}: {}", id, e.getMessage());
            }
        }
    }
}
