package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BaseError;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.infrastructure.client.user.ReputationPolicyClient;
import iuh.fit.postservice.infrastructure.client.user.ReputationPolicyClient.FeedWeightRequest;
import iuh.fit.postservice.infrastructure.client.user.ReputationPolicyClient.Policy;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Áp dụng quyền hạn theo bậc điểm uy tín (do user-service quyết định) lên việc đăng bài, chia sẻ, phát trực tiếp và
 * bình luận: giãn cách giữa hai lần đăng, giới hạn theo ngày, cấm hẳn ở bậc thấp. Không hỏi được user-service thì
 * cho phép (fail-open) để lỗi dịch vụ khác không chặn người dùng bình thường.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationGuard {

    /** Bài phát trực tiếp là một bài viết có tiêu đề bắt đầu bằng dấu hiệu này (xem LiveStreamContext ở FE). */
    public static final String LIVE_MARKER = "🔴 [ĐANG PHÁT TRỰC TIẾP]";
    /** Mã lỗi (trường code của phản hồi) khi tài khoản đang bị khóa; FE dùng mã này để hiện màn hình "Tài khoản đã bị khóa". */
    public static final int ACCOUNT_LOCKED_CODE = 40320;
    private static final String LOCKED_MESSAGE =
            "Tài khoản của bạn đang bị khóa do điểm uy tín quá thấp nên chưa thể sử dụng chức năng này.";
    private static final long POLICY_CACHE_MS = 15_000;
    private static final Duration DAY = Duration.ofHours(24);

    private final ReputationPolicyClient reputationPolicyClient;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    private final Map<UUID, CachedPolicy> cache = new ConcurrentHashMap<>();

    private record CachedPolicy(Policy policy, long expiresAtMillis) { }

    /** Kiểm tra người dùng có được đăng một bài viết (hoặc chia sẻ / phát trực tiếp) lúc này không. */
    public void checkCanPost(UUID userId, boolean live) {
        Policy policy = policyOf(userId);
        if (policy == null) {
            return;
        }
        String level = policy.levelLabel();
        if (policy.accountLocked()) {
            throw locked();
        }
        if (!policy.canPost()) {
            throw denied(403, "Điểm uy tín của bạn đang ở mức " + level + " nên bạn chưa thể đăng bài. "
                    + "Điểm sẽ tự hồi phục mỗi ngày không vi phạm.");
        }
        if (live && !policy.canGoLive()) {
            throw denied(403, "Điểm uy tín của bạn đang ở mức " + level + " nên bạn chưa thể phát trực tiếp. "
                    + "Điểm sẽ tự hồi phục mỗi ngày không vi phạm.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (policy.minPostIntervalSeconds() > 0) {
            LocalDateTime last = postRepository.findLatestCreatedAtByAuthor(userId);
            Duration wait = remaining(last, policy.minPostIntervalSeconds(), now);
            if (wait != null) {
                throw denied(429, "Điểm uy tín của bạn đang ở mức " + level + ": chỉ được đăng 1 bài mỗi "
                        + vi(Duration.ofSeconds(policy.minPostIntervalSeconds())) + ". Hãy thử lại sau " + vi(wait) + ".");
            }
        }
        if (policy.maxPostsPerDay() > 0
                && postRepository.countByAuthorIdAndCreatedAtAfter(userId, now.minus(DAY)) >= policy.maxPostsPerDay()) {
            throw denied(429, "Điểm uy tín của bạn đang ở mức " + level + ": chỉ được đăng tối đa "
                    + policy.maxPostsPerDay() + " bài trong 24 giờ. Hãy thử lại sau.");
        }
    }

    /** Kiểm tra người dùng có được bình luận (hoặc trả lời) lúc này không. */
    public void checkCanComment(UUID userId) {
        Policy policy = policyOf(userId);
        if (policy == null) {
            return;
        }
        String level = policy.levelLabel();
        if (policy.accountLocked()) {
            throw locked();
        }
        if (!policy.canComment()) {
            throw denied(403, "Điểm uy tín của bạn đang ở mức " + level + " nên bạn chưa thể bình luận.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (policy.minCommentIntervalSeconds() > 0) {
            LocalDateTime last = commentRepository.findLatestCreatedAtByAuthor(userId);
            Duration wait = remaining(last, policy.minCommentIntervalSeconds(), now);
            if (wait != null) {
                throw denied(429, "Điểm uy tín của bạn đang ở mức " + level + ": chỉ được bình luận 1 lần mỗi "
                        + vi(Duration.ofSeconds(policy.minCommentIntervalSeconds())) + ". Hãy thử lại sau " + vi(wait) + ".");
            }
        }
        if (policy.maxCommentsPerDay() > 0
                && commentRepository.countByAuthorIdAndCreatedAtAfter(userId, now.minus(DAY)) >= policy.maxCommentsPerDay()) {
            throw denied(429, "Điểm uy tín của bạn đang ở mức " + level + ": chỉ được bình luận tối đa "
                    + policy.maxCommentsPerDay() + " lần trong 24 giờ. Hãy thử lại sau.");
        }
    }

    /** Từ chối mọi thao tác ghi (thả cảm xúc, lưu bài...) của tài khoản đang bị khóa. */
    public void checkNotLocked(UUID userId) {
        Policy policy = policyOf(userId);
        if (policy != null && policy.accountLocked()) {
            throw locked();
        }
    }

    /** Hệ số xếp hạng bảng tin của các tác giả bị giảm độ ưu tiên vì điểm uy tín thấp (không có trong kết quả = 1). */
    public Map<UUID, Double> feedWeights(Collection<UUID> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<Map<UUID, Double>> response = reputationPolicyClient.getFeedWeights(new FeedWeightRequest(List.copyOf(authorIds)));
            return response != null && response.getData() != null ? response.getData() : Map.of();
        } catch (Exception e) {
            log.warn("Could not load reputation feed weights, ranking without them: {}", e.getMessage());
            return Map.of();
        }
    }

    private Policy policyOf(UUID userId) {
        long now = System.currentTimeMillis();
        CachedPolicy cached = cache.get(userId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.policy();
        }
        try {
            ApiResponse<ReputationPolicyClient.PolicyView> response = reputationPolicyClient.getPolicy(userId);
            Policy policy = response != null && response.getData() != null ? response.getData().policy() : null;
            if (policy != null) {
                if (cache.size() > 5_000) {
                    cache.clear();
                }
                cache.put(userId, new CachedPolicy(policy, now + POLICY_CACHE_MS));
            }
            return policy;
        } catch (Exception e) {
            log.warn("Could not load reputation policy of {}, allowing the action: {}", userId, e.getMessage());
            return null;
        }
    }

    /** Thời gian còn phải chờ nếu lần gần nhất cách hiện tại chưa đủ `intervalSeconds`; null nếu đã đủ. */
    private static Duration remaining(LocalDateTime last, int intervalSeconds, LocalDateTime now) {
        if (last == null) {
            return null;
        }
        Duration wait = Duration.ofSeconds(intervalSeconds).minus(Duration.between(last, now));
        return wait.isPositive() ? wait : null;
    }

    private static String vi(Duration duration) {
        long total = Math.max(1, duration.getSeconds());
        long minutes = total / 60;
        long seconds = total % 60;
        if (minutes == 0) {
            return seconds + " giây";
        }
        return seconds == 0 ? minutes + " phút" : minutes + " phút " + seconds + " giây";
    }

    private static BusinessException locked() {
        return denied(403, ACCOUNT_LOCKED_CODE, LOCKED_MESSAGE);
    }

    private static BusinessException denied(int status, String message) {
        return denied(status, status, message);
    }

    private static BusinessException denied(int status, int code, String message) {
        return new BusinessException(new BaseError() {
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
                return status;
            }
        });
    }
}
