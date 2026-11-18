package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.application.dto.PostResponse;
import iuh.fit.aiservice.application.service.rag.RagContextBuilder;
import iuh.fit.aiservice.application.service.rag.RagContextBuilder.Augmented;
import iuh.fit.aiservice.application.service.rag.RagContextBuilder.Passage;
import iuh.fit.aiservice.infrastructure.feign.PostFeignClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bước truy xuất của RAG cho trợ lý chat. Gọi tìm kiếm lai của post-service kèm JWT của người hỏi (được chuyển tiếp
 * bởi Feign interceptor), nên chỉ những bài người đó được phép xem mới trở thành tư liệu. Mọi lỗi đều bị nuốt để
 * trợ lý quay về trò chuyện thường, không làm hỏng tính năng chat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAssistantService {

    private static final int RETRIEVE_SIZE = 8;

    private final PostFeignClient postFeignClient;

    /** Truy xuất tư liệu và dựng lời nhắc. Không cần hoặc không tìm được tư liệu thì {@code sources} rỗng. */
    public Augmented prepare(String question) {
        if (!RagContextBuilder.shouldRetrieve(question)) {
            return new Augmented(question, List.of());
        }
        try {
            ApiResponse<List<PostResponse>> resp = postFeignClient.searchPosts(question.trim(), RETRIEVE_SIZE);
            List<PostResponse> posts = resp == null || resp.getData() == null ? List.of() : resp.getData();
            List<Passage> ranked = new ArrayList<>(posts.size());
            for (PostResponse p : posts) {
                ranked.add(new Passage(p.getId(), p.getContent()));
            }
            Augmented augmented = RagContextBuilder.augment(question, ranked);
            log.debug("RAG retrieved {} candidate posts, {} used as sources", posts.size(), augmented.sources().size());
            return augmented;
        } catch (Exception e) {
            log.warn("RAG retrieval failed, falling back to plain chat: {}", e.getMessage());
            return new Augmented(question, List.of());
        }
    }
}
