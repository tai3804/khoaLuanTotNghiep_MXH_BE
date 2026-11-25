package iuh.fit.aiservice.application.service.knowledge;

import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dựng vector store của kho tri thức từ các file Markdown trong {@code classpath:knowledge/}.
 *
 * <p>Quy trình: đọc file, cắt thành đoạn ({@link KnowledgeMarkdownParser}), so mã băm với bảng {@code knowledge_chunks}, chỉ tạo vector
 * (Gemini batch) cho đoạn mới hoặc đã đổi, ghi vào pgvector, xóa đoạn không còn trong tài liệu. Chạy lại khi không sửa gì thì
 * không tốn lượt gọi Gemini nào. Chạy nền lúc khởi động (tắt bằng {@code ai.knowledge.ingest-on-startup=false}) hoặc theo yêu cầu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    /** Số đoạn gửi trong một lời gọi batch embedding. */
    static final int BATCH_SIZE = 20;

    public record Report(int docs, int chunks, int embedded, int unchanged, int removed, int failed, long millis, String note) {
    }

    private final KnowledgeStore store;
    private final GeminiApiClient gemini;

    @Value("${ai.knowledge.ingest-on-startup:true}")
    private boolean ingestOnStartup;

    /** Thời gian chờ trước khi thử lại một lô bị Gemini từ chối (hạn mức tính theo phút) và số lần thử lại tối đa. */
    @Value("${ai.knowledge.retry-wait-ms:65000}")
    private long retryWaitMs;

    @Value("${ai.knowledge.max-retries:3}")
    private int maxRetries;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!ingestOnStartup) {
            log.info("Knowledge ingestion on startup is disabled");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Report r = ingest(false);
                log.info("Knowledge base ready: {}", r);
            } catch (Exception e) {
                log.warn("Knowledge ingestion failed: {}", e.getMessage(), e);
            }
        });
    }

    /** {@code force} = tạo lại vector cho mọi đoạn (ví dụ sau khi đổi model embedding). */
    public Report ingest(boolean force) {
        if (!running.compareAndSet(false, true)) {
            return new Report(0, 0, 0, 0, 0, 0, 0, "Đang có một lượt nạp khác chạy");
        }
        long t0 = System.currentTimeMillis();
        try {
            if (!store.ensureSchema()) {
                return new Report(0, 0, 0, 0, 0, 0, 0, "Cơ sở dữ liệu không có pgvector");
            }
            List<KnowledgeMarkdownParser.Doc> docs = loadDocs();
            Map<String, String> existing = force ? Map.of() : store.hashes();

            record Pending(String key, KnowledgeMarkdownParser.Doc doc, int order, String heading, String content, String text, String hash) {
            }
            List<Pending> toEmbed = new ArrayList<>();
            Set<String> allKeys = new LinkedHashSet<>();
            int chunks = 0, unchanged = 0;
            for (KnowledgeMarkdownParser.Doc doc : docs) {
                Map<String, Integer> seen = new HashMap<>();
                int order = 0;
                for (KnowledgeMarkdownParser.Chunk chunk : doc.chunks()) {
                    String base = doc.slug() + "::" + chunk.heading();
                    int n = seen.merge(base, 1, Integer::sum);
                    String key = n == 1 ? base : base + "~" + n;
                    String text = KnowledgeMarkdownParser.embeddingText(doc, chunk);
                    String hash = sha256(text);
                    allKeys.add(key);
                    chunks++;
                    if (hash.equals(existing.get(key))) {
                        store.touch(key, doc, order);
                        unchanged++;
                    } else {
                        toEmbed.add(new Pending(key, doc, order, chunk.heading(), chunk.body(), text, hash));
                    }
                    order++;
                }
            }

            int embedded = 0, failed = 0;
            for (int from = 0; from < toEmbed.size(); from += BATCH_SIZE) {
                List<Pending> batch = toEmbed.subList(from, Math.min(from + BATCH_SIZE, toEmbed.size()));
                List<List<Float>> vectors = embedBatchWithRetry(batch.stream().map(Pending::text).toList());
                for (int i = 0; i < batch.size(); i++) {
                    Pending p = batch.get(i);
                    List<Float> vector = vectors.size() == batch.size() ? vectors.get(i) : List.of();
                    if (vector.isEmpty()) {
                        failed++;
                        log.warn("Could not embed knowledge chunk {}", p.key());
                        continue;
                    }
                    store.upsert(p.key(), p.doc(), p.order(), p.heading(), p.content(), p.hash(), KnowledgeStore.toVectorLiteral(vector));
                    embedded++;
                }
            }

            // Chỉ xóa đoạn cũ khi đọc được tài liệu; lỗi tạo vector không làm mất đoạn cũ còn dùng được
            int removed = docs.isEmpty() ? 0 : store.deleteNotIn(allKeys);
            return new Report(docs.size(), chunks, embedded, unchanged, removed, failed, System.currentTimeMillis() - t0,
                    docs.isEmpty() ? "Không tìm thấy tài liệu nào trong classpath:knowledge/" : "");
        } finally {
            running.set(false);
        }
    }

    /**
     * Một lô lỗi thường do Gemini giới hạn tần suất theo phút (HTTP 429). Chờ hết phút rồi thử lại chính lô đó, thay vì gọi từng đoạn
     * một (mỗi lời gọi lẻ lại tính thêm vào hạn mức và còn làm các câu hỏi thật của người dùng cũng bị chặn). Hết số lần thử thì
     * trả về rỗng: các đoạn đó được tính là lỗi và sẽ được nạp ở lần chạy sau (mã băm giúp không phải làm lại đoạn đã xong).
     */
    private List<List<Float>> embedBatchWithRetry(List<String> texts) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            List<List<Float>> vectors = gemini.generateBatchEmbeddings(texts);
            if (vectors.size() == texts.size() && vectors.stream().noneMatch(List::isEmpty)) return vectors;
            if (attempt < maxRetries) {
                log.warn("Embedding batch of {} failed (attempt {}/{}), waiting {} ms for the rate-limit window", texts.size(), attempt + 1, maxRetries + 1, retryWaitMs);
                try {
                    Thread.sleep(retryWaitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return List.of();
    }

    public Map<String, Integer> status() {
        if (!store.ensureSchema()) return Map.of();
        return store.countByDoc();
    }

    List<KnowledgeMarkdownParser.Doc> loadDocs() {
        List<KnowledgeMarkdownParser.Doc> docs = new ArrayList<>();
        try {
            // Chỉ định class loader của ứng dụng: luồng nền (ForkJoinPool) dùng class loader hệ thống, không thấy BOOT-INF/classes
            // trong jar thực thi nên không tìm được tài liệu (test chạy trên thư mục thường thì vẫn thấy).
            Resource[] resources = new PathMatchingResourcePatternResolver(KnowledgeIngestionService.class.getClassLoader())
                    .getResources("classpath*:knowledge/*.md");
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            for (Resource r : sorted) {
                String name = r.getFilename();
                if (name == null || name.equalsIgnoreCase("README.md") || name.startsWith("_")) continue;
                String md = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                docs.add(KnowledgeMarkdownParser.parse(Path.of(name).getFileName().toString(), md));
            }
        } catch (Exception e) {
            log.warn("Could not read knowledge documents: {}", e.getMessage());
        }
        return docs;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
