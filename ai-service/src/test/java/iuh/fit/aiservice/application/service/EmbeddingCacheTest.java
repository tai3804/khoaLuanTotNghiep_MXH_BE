package iuh.fit.aiservice.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingCacheTest {

    private static final List<Float> VECTOR = List.of(0.1f, 0.2f, 0.3f);

    @Test
    void sameQueryIsComputedOnce() {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();

        List<Float> first = cache.getOrCompute("du lịch biển", t -> {
            calls.incrementAndGet();
            return VECTOR;
        });
        List<Float> second = cache.getOrCompute("du lịch biển", t -> {
            calls.incrementAndGet();
            return VECTOR;
        });

        assertEquals(1, calls.get());
        assertEquals(VECTOR, first);
        assertEquals(VECTOR, second);
    }

    @Test
    void keyIgnoresCaseAndExtraWhitespace() {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();

        cache.getOrCompute("Du Lịch  Biển", t -> {
            calls.incrementAndGet();
            return VECTOR;
        });
        cache.getOrCompute("  du lịch biển ", t -> {
            calls.incrementAndGet();
            return VECTOR;
        });

        assertEquals(1, calls.get());
    }

    @Test
    void failedOrEmptyResultIsNeverCached() {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(cache.getOrCompute("câu hỏi", t -> {
            calls.incrementAndGet();
            return List.of();
        }).isEmpty());
        assertEquals(0, cache.size());

        List<Float> retry = cache.getOrCompute("câu hỏi", t -> {
            calls.incrementAndGet();
            return VECTOR;
        });

        assertEquals(2, calls.get());
        assertEquals(VECTOR, retry);
    }

    @Test
    void longTextBypassesTheCache() {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();
        String longText = "a".repeat(EmbeddingCache.MAX_CACHEABLE_CHARS + 1);

        cache.getOrCompute(longText, t -> {
            calls.incrementAndGet();
            return VECTOR;
        });
        cache.getOrCompute(longText, t -> {
            calls.incrementAndGet();
            return VECTOR;
        });

        assertEquals(2, calls.get());
        assertEquals(0, cache.size());
    }

    @Test
    void evictsLeastRecentlyUsedBeyondCapacity() {
        EmbeddingCache cache = new EmbeddingCache();
        for (int i = 0; i < EmbeddingCache.MAX_ENTRIES + 25; i++) {
            cache.getOrCompute("query " + i, t -> VECTOR);
        }
        assertEquals(EmbeddingCache.MAX_ENTRIES, cache.size());
    }

    @Test
    void concurrentIdenticalQueriesShareOneCall() throws Exception {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<List<Float>> leader = pool.submit(() -> cache.getOrCompute("tìm bạn", t -> {
                calls.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return VECTOR;
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // Ba dịch vụ khác hỏi cùng câu trong lúc lời gọi đầu tiên chưa xong
            Future<List<Float>> f1 = pool.submit(() -> cache.getOrCompute("tìm bạn", t -> {
                calls.incrementAndGet();
                return VECTOR;
            }));
            Future<List<Float>> f2 = pool.submit(() -> cache.getOrCompute("Tìm bạn", t -> {
                calls.incrementAndGet();
                return VECTOR;
            }));
            Future<List<Float>> f3 = pool.submit(() -> cache.getOrCompute("tìm  bạn", t -> {
                calls.incrementAndGet();
                return VECTOR;
            }));
            Thread.sleep(150);
            release.countDown();

            assertEquals(VECTOR, leader.get(5, TimeUnit.SECONDS));
            assertEquals(VECTOR, f1.get(5, TimeUnit.SECONDS));
            assertEquals(VECTOR, f2.get(5, TimeUnit.SECONDS));
            assertEquals(VECTOR, f3.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get(), "chỉ một lời gọi Gemini cho bốn yêu cầu cùng lúc");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void blankTextReturnsEmptyWithoutCalling() {
        EmbeddingCache cache = new EmbeddingCache();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(cache.getOrCompute("   ", t -> {
            calls.incrementAndGet();
            return VECTOR;
        }).isEmpty());
        assertEquals(0, calls.get());
    }
}
