package iuh.fit.postservice.application.service;

import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.entities.SavedPost;
import iuh.fit.postservice.domain.enums.ReactionType;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.SavedPostRepository;
import iuh.fit.postservice.presentation.dto.response.CommentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service làm giàu dữ liệu bài viết (Enrichment Service) giúp gộp tất cả thông tin:
 * Cảm xúc (Reactions), Trạng thái lưu (Saved), và Bình luận xem trước (Preview Comments)
 * vào chung trong 1 request duy nhất, giải quyết triệt để bài toán N+1 query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEnrichmentService {

    private final ReactionRepository reactionRepository;
    private final SavedPostRepository savedPostRepository;
    private final CommentRepository commentRepository;

    public void enrichPosts(List<GetPostDetailResult> posts, UUID viewerId) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        List<UUID> postIds = posts.stream()
                .map(GetPostDetailResult::getId)
                .filter(Objects::nonNull)
                .toList();

        if (postIds.isEmpty()) {
            return;
        }

        // 1. Cảm xúc: đếm theo loại bằng GROUP BY (không tải từng dòng reaction) + riêng cảm xúc của viewer
        Map<UUID, List<String>> topTypesByPost = new HashMap<>();
        Map<UUID, String> myReactionByPost = new HashMap<>();
        try {
            for (Object[] row : reactionRepository.countTypesByPostIds(postIds)) {
                List<String> types = topTypesByPost.computeIfAbsent((UUID) row[0], k -> new ArrayList<>());
                if (types.size() < 5) types.add(((ReactionType) row[1]).name());
            }
            if (viewerId != null) {
                for (Reaction r : reactionRepository.findByUserIdAndPostIdIn(viewerId, postIds)) {
                    myReactionByPost.put(r.getPostId(), r.getType().name());
                }
            }
        } catch (Exception e) {
            log.warn("[PostEnrichment] Lỗi khi tải reactions: {}", e.getMessage());
        }

        // 2. Lấy trạng thái lưu bài của viewer trong 1 câu SQL batch duy nhất
        Set<UUID> savedPostIds = Collections.emptySet();
        if (viewerId != null) {
            try {
                savedPostIds = savedPostRepository.findByUserIdAndPostIdIn(viewerId, postIds).stream()
                        .map(SavedPost::getPostId)
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("[PostEnrichment] Lỗi khi tải saved posts: {}", e.getMessage());
            }
        }

        // 3. Bình luận xem trước: 3 bình luận mới nhất MỖI bài bằng một truy vấn window function
        Map<UUID, List<Comment>> commentsByPost = Collections.emptyMap();
        try {
            commentsByPost = commentRepository.findLatestTopLevelPerPost(postIds, 3).stream()
                    .collect(Collectors.groupingBy(Comment::getPostId));
        } catch (Exception e) {
            log.warn("[PostEnrichment] Lỗi khi tải preview comments: {}", e.getMessage());
        }

        // 4. Gán thông tin đã gộp vào từng bài viết
        for (GetPostDetailResult post : posts) {
            UUID postId = post.getId();

            // A. Cảm xúc (Top reactions + myReaction + isLiked)
            post.setTopReactionTypes(topTypesByPost.getOrDefault(postId, Collections.emptyList()));
            String myReaction = myReactionByPost.get(postId);
            post.setLiked(myReaction != null);
            post.setMyReaction(myReaction);

            // B. Trạng thái lưu bài
            post.setSaved(savedPostIds.contains(postId));

            // C. 3 bình luận xem trước
            List<Comment> postComments = commentsByPost.getOrDefault(postId, Collections.emptyList());
            List<CommentResponse> previewComments = postComments.stream()
                    .limit(3)
                    .map(c -> CommentResponse.builder()
                            .id(c.getId())
                            .postId(c.getPostId())
                            .authorId(c.getAuthorId())
                            .parentCommentId(c.getParentCommentId())
                            .content(c.getContent())
                            .mediaUrl(c.getMediaUrl())
                            .mediaKey(c.getMediaKey())
                            .likeCount(c.getLikeCount())
                            .createdAt(c.getCreatedAt())
                            .build())
                    .toList();
            post.setPreviewComments(previewComments);
        }
    }

    public void enrichPost(GetPostDetailResult post, UUID viewerId) {
        if (post != null) {
            enrichPosts(List.of(post), viewerId);
        }
    }
}

