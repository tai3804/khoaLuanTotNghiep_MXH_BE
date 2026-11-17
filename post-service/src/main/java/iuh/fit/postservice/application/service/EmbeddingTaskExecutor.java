package iuh.fit.postservice.application.service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hàng đợi chạy nền riêng cho việc sinh embedding (gọi ai-service/Gemini, chặn I/O).
 *
 * <p>Trước đây dùng {@code CompletableFuture.runAsync} trên {@code ForkJoinPool.commonPool()}, vốn chỉ có (số CPU - 1) luồng
 * và dùng chung với mọi thứ khác trong JVM: một đợt đăng bài/cập nhật hồ sơ dồn dập sẽ chiếm hết luồng bằng các lời gọi HTTP
 * dài và làm đói các tác vụ song song khác (parallel stream, async khác). Ở đây: số luồng cố định nhỏ (giới hạn tốc độ gọi
 * Gemini), hàng đợi có chặn trên, hết chỗ thì bỏ tác vụ và để bước backfill định kỳ nạp bù thay vì làm tràn bộ nhớ.
 */
final class EmbeddingTaskExecutor {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final ThreadPoolExecutor POOL = createPool();

    private EmbeddingTaskExecutor() {
    }

    private static ThreadPoolExecutor createPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), r -> {
            Thread t = new Thread(r, "embedding-async-" + SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /** @return false nếu hàng đợi đầy và tác vụ bị bỏ */
    static boolean submit(Runnable task) {
        try {
            POOL.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }
}
