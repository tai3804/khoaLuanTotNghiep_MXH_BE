package iuh.fit.aiservice.application.service.knowledge;

import iuh.fit.aiservice.application.service.EmbeddingCache;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Truy xuất các đoạn tài liệu liên quan tới câu hỏi của người dùng. Câu hỏi được tạo vector qua {@link EmbeddingCache} (cùng khóa với
 * lần tìm bài viết nên cả hai dùng chung MỘT lời gọi Gemini), rồi lấy các đoạn gần nhất trong pgvector.
 *
 * <p>Chỉ giữ đoạn có độ giống cosine từ {@code ai.knowledge.min-similarity} trở lên: vector Gemini dồn thành cụm nên hai văn bản không
 * liên quan vẫn đạt ~0,55-0,65, một ngưỡng thấp sẽ luôn trả về "tài liệu gần nhất" dù chẳng liên quan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    public record Hit(String key, String docSlug, String docTitle, String heading, String content, double similarity) {
    }

    private final KnowledgeStore store;
    private final GeminiApiClient gemini;
    private final EmbeddingCache embeddingCache;

    @Value("${ai.knowledge.min-similarity:0.70}")
    private double minSimilarity;

    /** Số ứng viên lấy từ pgvector trước khi lọc theo đối tượng đọc và ngưỡng. */
    private static final int CANDIDATES = 12;

    /** Mọi người dùng thấy tài liệu cho người dùng và cho lập trình viên; tài liệu kiểm duyệt/quản trị chỉ dành cho vai trò đó. */
    public static Set<String> audiencesFor(Collection<String> roles) {
        Set<String> out = new java.util.LinkedHashSet<>(List.of("user", "developer"));
        boolean admin = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ROLE_ADMIN"));
        boolean moderator = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ROLE_MODERATOR"));
        if (admin || moderator) out.add("moderator");
        if (admin) out.add("admin");
        return out;
    }

    public List<Hit> search(String question, int k, Set<String> audiences) {
        return search(question, k, audiences, minSimilarity);
    }

    public List<Hit> search(String question, int k, Set<String> audiences, double minSim) {
        if (question == null || question.isBlank() || k <= 0) return List.of();
        try {
            if (!store.ensureSchema()) return List.of();
            List<Float> vector = embeddingCache.getOrCompute(question.trim(), gemini::generateEmbedding);
            if (vector.isEmpty()) return List.of();
            return store.nearest(KnowledgeStore.toVectorLiteral(vector), CANDIDATES).stream()
                    .filter(c -> audiences == null || audiences.contains(c.audience().toLowerCase(Locale.ROOT)))
                    .filter(c -> c.similarity() >= minSim)
                    .limit(k)
                    .map(c -> new Hit(c.key(), c.docSlug(), c.docTitle(), c.heading(), c.content(), c.similarity()))
                    .toList();
        } catch (Exception e) {
            log.warn("Knowledge search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
