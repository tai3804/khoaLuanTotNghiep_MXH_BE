package iuh.fit.adminservice.application.service;

import iuh.fit.adminservice.application.service.HashtagTrendAnalyzer.PostSample;
import iuh.fit.adminservice.application.service.HashtagTrendAnalyzer.Trend;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashtagTrendAnalyzerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    private static PostSample post(String content, int hoursAgo) {
        return new PostSample(content, NOW.minusHours(hoursAgo), 0);
    }

    @Test
    void risingHashtagHasPositiveGrowthAndHigherScoreThanSteadyOne() {
        List<PostSample> posts = new ArrayList<>();
        // #iuh: 1 bài ở cửa sổ trước, 5 bài ở cửa sổ hiện tại
        posts.add(post("#iuh cũ", 30));
        for (int i = 0; i < 5; i++) posts.add(post("#iuh mới " + i, 2 + i));
        // #java: 3 bài mỗi cửa sổ
        for (int i = 0; i < 3; i++) { posts.add(post("#java a" + i, 26 + i)); posts.add(post("#java b" + i, 3 + i)); }

        List<Trend> trends = HashtagTrendAnalyzer.analyze(posts, NOW, 10);
        assertEquals("#iuh", trends.get(0).tag());
        Trend iuh = trends.get(0);
        assertEquals(5, iuh.currentCount());
        assertEquals(1, iuh.previousCount());
        assertEquals(200.0, iuh.growthPercent(), 1e-9);              // (5 - 1) / (1 + 1) = 200%
        assertEquals((5 - 2) / Math.sqrt(2), iuh.burstScore(), 0.01); // z-score Poisson
        Trend java = trends.stream().filter(t -> t.tag().equals("#java")).findFirst().orElseThrow();
        assertTrue(iuh.burstScore() > java.burstScore());
        assertEquals(24, iuh.windowHours());
    }

    @Test
    void decliningHashtagIsExcluded() {
        List<PostSample> posts = new ArrayList<>();
        for (int i = 0; i < 4; i++) posts.add(post("#cu cũ " + i, 26 + i));
        posts.add(post("#cu mới", 3));
        posts.add(post("#moi", 4));
        List<Trend> trends = HashtagTrendAnalyzer.analyzeWindow(posts, NOW, 24, 10);
        assertFalse(trends.stream().anyMatch(t -> t.tag().equals("#cu")));
        assertTrue(trends.stream().anyMatch(t -> t.tag().equals("#moi")));
    }

    @Test
    void hashtagCountedOncePerPostAndCaseInsensitive() {
        List<PostSample> posts = List.of(post("#IUH #iuh #Iuh", 1), post("xin chào #iuh", 2));
        Trend t = HashtagTrendAnalyzer.analyzeWindow(posts, NOW, 24, 10).get(0);
        assertEquals(2, t.currentCount());
    }

    @Test
    void windowWidensWhenRecentWindowIsSparse() {
        // Chỉ có bài cách đây 2 ngày: cửa sổ 24 giờ rỗng nên phải mở rộng lên 72 giờ
        List<PostSample> posts = new ArrayList<>();
        for (int i = 0; i < 6; i++) posts.add(post("#ontap bài " + i, 48 + i));
        List<Trend> trends = HashtagTrendAnalyzer.analyze(posts, NOW, 10);
        assertEquals(1, trends.size());
        assertEquals(72, trends.get(0).windowHours());
    }

    @Test
    void viralNeedsHighScoreAndMinimumCount() {
        List<PostSample> posts = new ArrayList<>();
        for (int i = 0; i < 10; i++) posts.add(post("#hot " + i, 1 + i));
        Trend t = HashtagTrendAnalyzer.analyze(posts, NOW, 10).get(0);
        assertTrue(t.viral());
        assertEquals(1000.0, t.growthPercent(), 1e-9);                // (10 - 0) / (0 + 1)

        Trend small = HashtagTrendAnalyzer.analyze(List.of(post("#it", 1), post("#it", 2)), NOW, 10).get(0);
        assertFalse(small.viral());
    }

    @Test
    void ignoresPostsWithoutContentOrTimestamp() {
        List<PostSample> posts = new ArrayList<>();
        posts.add(new PostSample(null, NOW.minusHours(1), 0));
        posts.add(new PostSample("#a", null, 0));
        assertTrue(HashtagTrendAnalyzer.analyze(posts, NOW, 10).isEmpty());
    }

    @Test
    void engagementSumsInteractionsInCurrentWindowOnly() {
        List<PostSample> posts = List.of(
                new PostSample("#x", NOW.minusHours(2), 10),
                new PostSample("#x", NOW.minusHours(5), 4),
                new PostSample("#x", NOW.minusHours(30), 100));
        Trend t = HashtagTrendAnalyzer.analyzeWindow(posts, NOW, 24, 10).get(0);
        assertEquals(14, t.engagement());
    }
}
