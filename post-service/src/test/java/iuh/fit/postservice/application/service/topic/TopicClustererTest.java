package iuh.fit.postservice.application.service.topic;

import iuh.fit.postservice.application.service.topic.TrendingTopicService.Sample;
import iuh.fit.postservice.application.service.topic.TrendingTopicService.Topic;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicClustererTest {

    /** Điểm quanh một hướng chính trong không gian 8 chiều, nhiễu nhỏ. */
    private static float[] around(int axis, Random rnd) {
        float[] v = new float[8];
        for (int i = 0; i < 8; i++) v[i] = (float) (rnd.nextGaussian() * 0.08);
        v[axis] += 1f;
        return v;
    }

    private static float[][] blobs(int[] axes, int perBlob, long seed) {
        Random rnd = new Random(seed);
        List<float[]> out = new ArrayList<>();
        for (int axis : axes) for (int i = 0; i < perBlob; i++) out.add(around(axis, rnd));
        return out.toArray(new float[0][]);
    }

    @Test
    void silhouetteChoosesTheTrueNumberOfTopics() {
        float[][] data = blobs(new int[]{0, 3, 6}, 15, 1);
        TopicClusterer.Clustering c = TopicClusterer.best(data, 2, 6, 42);
        assertEquals(3, c.k());
        assertTrue(c.silhouette() > 0.8, "cụm tách bạch phải có silhouette cao: " + c.silhouette());

        // Mỗi khối 15 điểm phải thuộc đúng một cụm
        for (int b = 0; b < 3; b++) {
            int first = c.assignment()[b * 15];
            for (int i = 0; i < 15; i++) assertEquals(first, c.assignment()[b * 15 + i]);
        }
    }

    @Test
    void centroidsAreUnitLengthAndResultIsDeterministic() {
        float[][] data = blobs(new int[]{1, 4}, 10, 2);
        TopicClusterer.Clustering a = TopicClusterer.best(data, 2, 4, 7);
        TopicClusterer.Clustering b = TopicClusterer.best(data, 2, 4, 7);
        assertEquals(a.k(), b.k());
        assertTrue(java.util.Arrays.equals(a.assignment(), b.assignment()));
        for (float[] centroid : a.centroids()) {
            double s = 0;
            for (float x : centroid) s += (double) x * x;
            assertEquals(1.0, Math.sqrt(s), 1e-5);
        }
    }

    @Test
    void tinyInputsDoNotCrash() {
        assertEquals(0, TopicClusterer.best(new float[0][], 2, 4, 1).k());
        assertEquals(1, TopicClusterer.best(blobs(new int[]{0}, 2, 1), 2, 4, 1).k());
    }

    // ---------- Phát hiện chủ đề đang nổi ----------

    private static Sample sample(float[] v, int hoursAgo, String content) {
        return new Sample(UUID.randomUUID(), UUID.randomUUID(), content, LocalDateTime.now().minusHours(hoursAgo), 1, v);
    }

    @Test
    void aTopicThatGrewFastIsRankedFirstAndFlaggedAsTrending() {
        Random rnd = new Random(5);
        List<Sample> current = new ArrayList<>();
        List<Sample> previous = new ArrayList<>();
        // Chủ đề A (trục 0): 12 bài hiện tại, 1 bài trước đó -> bùng nổ
        for (int i = 0; i < 12; i++) current.add(sample(around(0, rnd), i, "Bài về chủ đề A #chuA"));
        previous.add(sample(around(0, rnd), 40, "cũ A"));
        // Chủ đề B (trục 4): 8 bài hiện tại, 8 bài trước đó -> ổn định
        for (int i = 0; i < 8; i++) current.add(sample(around(4, rnd), i, "Bài về chủ đề B #chuB"));
        for (int i = 0; i < 8; i++) previous.add(sample(around(4, rnd), 40 + i, "cũ B"));

        TrendingTopicService.Report report = TrendingTopicService.analyze(1, current, previous);
        assertEquals(2, report.k());
        Topic top = report.topics().get(0);
        assertEquals(12, top.posts());
        assertEquals(1, top.previousPosts());
        assertTrue(top.trending());
        assertTrue(top.burstScore() > 2.0);
        assertTrue(top.hashtags().contains("#chua"), "nhãn cụm lấy từ hashtag phổ biến nhất trong cụm");
        Topic second = report.topics().get(1);
        assertEquals(8, second.previousPosts());
        assertTrue(second.burstScore() <= 0);
    }

    @Test
    void tooFewPostsGiveAnEmptyReport() {
        TrendingTopicService.Report r = TrendingTopicService.analyze(7, List.of(sample(new float[]{1, 0}, 1, "x")), List.of());
        assertTrue(r.topics().isEmpty());
    }
}
