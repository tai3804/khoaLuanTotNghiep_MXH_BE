package iuh.fit.aiservice.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Bộ nhớ đệm vector embedding dùng chung cho mọi dịch vụ gọi {@code /ai/embeddings/generate}.
 *
 * <p>Mỗi lần gọi Gemini mất khoảng 0,5 giây và tính vào hạn mức API. Một lần tìm kiếm trước đây có thể gọi tới 6 lần cho
 * cùng một câu (người, nhóm, bài viết, lặp lại ở ô gợi ý và trang kết quả). Bộ đệm này làm hai việc:
 * <ul>
 *   <li><b>LRU có hạn dùng</b>: cùng một văn bản (không phân biệt hoa thường, khoảng trắng thừa) chỉ gọi Gemini một lần
 *       trong {@link #TTL_MILLIS}.</li>
 *   <li><b>Gộp lời gọi đồng thời</b>: nhiều dịch vụ hỏi cùng một câu cùng lúc thì chỉ một lời gọi đi ra ngoài, những lời
 *       gọi còn lại chờ kết quả đó.</li>
 * </ul>
 * Kết quả rỗng (Gemini lỗi hoặc timeout) <b>không bao giờ</b> được ghi nhớ, nên lỗi tạm thời không bị kéo dài.
 */
@Slf4j
@Component
public class EmbeddingCache {

    static final int MAX_ENTRIES = 1000;
    static final long TTL_MILLIS = TimeUnit.HOURS.toMillis(6);
    /** Chờ lời gọi đang chạy tối đa chừng này; quá hạn thì tự gọi lấy để không treo yêu cầu. */
    static final long FOLLOWER_WAIT_SECONDS = 12;
    /**
     * Chỉ ghi nhớ văn bản ngắn (câu truy vấn tìm kiếm). Nội dung bài viết, hồ sơ dài... cũng đi qua cùng endpoint nhưng mỗi cái
     * một khác, không bao giờ được hỏi lại, nên không đáng chiếm chỗ trong bộ đệm.
     */
    static final int MAX_CACHEABLE_CHARS = 300;

    private record Entry(float[] vector, long expiresAt) {
    }

    private final Map<String, Entry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private final ConcurrentHashMap<String, CompletableFuture<float[]>> inFlight = new ConcurrentHashMap<>();

    /** Lấy vector từ bộ đệm, hoặc gọi {@code loader} đúng một lần cho các yêu cầu trùng nhau đang chạy cùng lúc. */
    public List<Float> getOrCompute(String text, Function<String, List<Float>> loader) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        if (text.length() > MAX_CACHEABLE_CHARS) {
            return loader.apply(text);
        }
        String key = normalize(text);
        long now = System.currentTimeMillis();

        Entry hit = cache.get(key);
        if (hit != null) {
            if (hit.expiresAt() > now) {
                return toList(hit.vector());
            }
            cache.remove(key);
        }

        CompletableFuture<float[]> mine = new CompletableFuture<>();
        CompletableFuture<float[]> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            try {
                return toList(running.get(FOLLOWER_WAIT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.debug("Embedding in flight for '{}' failed or timed out, computing directly: {}", key, e.toString());
                return loader.apply(text);
            }
        }

        try {
            List<Float> vector = loader.apply(text);
            float[] arr = toArray(vector);
            if (arr.length > 0) {
                cache.put(key, new Entry(arr, System.currentTimeMillis() + TTL_MILLIS));
            }
            mine.complete(arr);
            return vector;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    int size() {
        return cache.size();
    }

    /** Khoá bộ đệm: bỏ khoảng trắng thừa, không phân biệt hoa thường. */
    static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static float[] toArray(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return new float[0];
        }
        float[] arr = new float[vector.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = vector.get(i);
        }
        return arr;
    }

    private static List<Float> toList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) {
            out.add(v);
        }
        return out;
    }
}
