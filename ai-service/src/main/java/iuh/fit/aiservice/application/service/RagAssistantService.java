package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.application.dto.PostResponse;
import iuh.fit.aiservice.application.service.knowledge.KnowledgeSearchService;
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
import java.util.Set;

/**
 * Bước truy xuất của RAG cho trợ lý chat, từ hai nguồn:
 * <ol>
 * <li><b>Kho tri thức</b> (tài liệu hướng dẫn trong pgvector): trả lời câu hỏi về cách dùng và quy định của KLTN Social. Chỉ những đoạn
 * đủ giống mới được dùng, và người hỏi chỉ thấy tài liệu đúng vai trò của mình.</li>
 * <li><b>Bài viết</b>: tìm kiếm lai của post-service kèm JWT của người hỏi (được chuyển tiếp bởi Feign interceptor), nên chỉ những bài
 * người đó được phép xem mới trở thành tư liệu.</li>
 * </ol>
 * Mỗi nguồn lỗi độc lập và bị nuốt: nguồn còn lại vẫn dùng được, và nếu cả hai đều lỗi thì trợ lý quay về trò chuyện thường.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAssistantService {

    private static final int RETRIEVE_SIZE = 8;
    private static final int KNOWLEDGE_TOP_K = 3;

    private final PostFeignClient postFeignClient;
    private final KnowledgeSearchService knowledgeSearch;

    /** Truy xuất tư liệu và dựng lời nhắc. Không cần hoặc không tìm được tư liệu thì {@code sources} rỗng. */
    public Augmented prepare(String question, Set<String> audiences) {
        if (!RagContextBuilder.shouldRetrieve(question)) {
            return new Augmented(question, List.of());
        }
        List<Passage> ranked = new ArrayList<>();

        // 1) Tài liệu hướng dẫn. Chạy trước để vector của câu hỏi nằm sẵn trong bộ nhớ đệm cho lần tìm bài viết ngay sau đó.
        try {
            for (KnowledgeSearchService.Hit hit : knowledgeSearch.search(question.trim(), KNOWLEDGE_TOP_K, audiences)) {
                ranked.add(Passage.doc(hit.key(), hit.docTitle() + " > " + hit.heading(), hit.content()));
            }
        } catch (Exception e) {
            log.warn("Knowledge retrieval failed, continuing without documents: {}", e.getMessage());
        }

        // 2) Bài viết của người dùng
        try {
            ApiResponse<List<PostResponse>> resp = postFeignClient.searchPosts(question.trim(), RETRIEVE_SIZE);
            List<PostResponse> posts = resp == null || resp.getData() == null ? List.of() : resp.getData();
            for (PostResponse p : posts) {
                ranked.add(new Passage(p.getId(), p.getContent()));
            }
        } catch (Exception e) {
            log.warn("Post retrieval failed, continuing without posts: {}", e.getMessage());
        }

        Augmented augmented = RagContextBuilder.augment(question, ranked);
        log.debug("RAG retrieved {} candidates, {} used as sources", ranked.size(), augmented.sources().size());
        return augmented;
    }
}
