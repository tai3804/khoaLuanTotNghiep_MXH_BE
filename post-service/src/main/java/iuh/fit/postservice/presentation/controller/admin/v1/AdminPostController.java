package iuh.fit.postservice.presentation.controller.admin.v1;

import iuh.fit.commonframework.event.PostDeletedEvent;
import iuh.fit.postservice.application.service.ranking.RankedFeedCache;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /api/v1/posts/** là đường dẫn công khai ở tầng filter nên quyền phải được kiểm tra bằng @PreAuthorize;
 * kiểm tra thủ công trước đây bỏ qua khi danh sách role rỗng (request chưa đăng nhập) nên ai cũng xoá được bài.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/posts/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class AdminPostController {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostCacheEvictor postCacheEvictor;
    RankedFeedCache rankedFeedCache;
    KafkaTemplate<String, Object> kafkaTemplate;
    iuh.fit.postservice.application.service.search.eval.SearchEvaluationService searchEvaluationService;
    iuh.fit.postservice.application.service.topic.TrendingTopicService trendingTopicService;

    /**
     * Đánh giá offline chất lượng tìm kiếm: nhận các truy vấn đã gán nhãn (postId + mức liên quan 0–3), trả về Precision@k,
     * Recall@k, MAP, MRR, NDCG@k của từng phương án xếp hạng (kiểm định chéo cho phương án đã học) và bộ trọng số khuyến nghị
     * để dán vào cấu hình SEARCH_WEIGHTS.
     */
    @PostMapping("/search-eval")
    public ResponseEntity<iuh.fit.postservice.application.service.search.eval.SearchEvaluationService.Report> evaluateSearch(
            @RequestBody iuh.fit.postservice.application.service.search.eval.SearchEvaluationService.EvalRequest request) {
        return ResponseEntity.ok(searchEvaluationService.evaluate(request));
    }

    /** Các chủ đề đang nổi, gom cụm từ embedding của bài viết gần đây (k-means cầu + chọn k bằng silhouette). */
    @GetMapping("/trending-topics")
    public ResponseEntity<iuh.fit.postservice.application.service.topic.TrendingTopicService.Report> trendingTopics(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        return ResponseEntity.ok(trendingTopicService.analyze(days));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countPosts() {
        return ResponseEntity.ok(postRepository.countByDeletedFalse());
    }

    @DeleteMapping("/{postId}")
    @Transactional
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        log.info("Admin requested delete for post: {}", postId);

        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        Post post = postOpt.get();
        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(postId);
        List<String> fileKeys = new ArrayList<>();
        for (PostMedia media : mediaList) {
            String targetKey = (media.getFileKey() != null && !media.getFileKey().isBlank())
                    ? media.getFileKey()
                    : media.getFileUrl();
            if (targetKey != null && !targetKey.isBlank()) {
                fileKeys.add(targetKey);
            }
        }

        post.setDeleted(true);
        post.setArchived(true);
        post.setGroupPinnedAt(null);
        postRepository.save(post);
        postMediaRepository.deleteByPostId(postId);

        if (post.getAuthorId() != null) {
            rankedFeedCache.evictViewer(post.getAuthorId());
        }
        postCacheEvictor.evictAllWithRecheck();

        try {
            kafkaTemplate.send("post.deleted", PostDeletedEvent.builder()
                    .postId(postId)
                    .authorId(post.getAuthorId())
                    .fileKeys(fileKeys)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish PostDeletedEvent for admin-deleted post {}: {}", postId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
