package iuh.fit.postservice.application.service.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hồ sơ sở thích có tính cả câu tìm kiếm")
class UserInterestProfileSearchTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 12, 0);

    private static float[] axis(int i) {
        float[] v = new float[8];
        v[i] = 1f;
        return v;
    }

    @Test
    @DisplayName("Câu tìm kiếm có trọng số ×1 và giảm một nửa sau 30 ngày")
    void searchWeightDecays() {
        List<UserInterestProfile.WeightedVector> w = UserInterestProfile.searchWeights(List.of(
                new UserInterestProfile.Search("phở", axis(0), NOW),
                new UserInterestProfile.Search("bóng đá", axis(1), NOW.minusDays(30))), NOW);
        assertEquals(2, w.size());
        assertEquals(1.0, w.get(0).weight(), 1e-9);
        assertEquals(0.5, w.get(1).weight(), 1e-9);
    }

    @Test
    @DisplayName("Chỉ giữ tối đa 20 câu tìm gần nhất")
    void keepsLatestSearches() {
        List<UserInterestProfile.Search> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) many.add(new UserInterestProfile.Search("q" + i, axis(0), NOW.minusHours(i)));
        assertEquals(UserInterestProfile.MAX_SEARCHES, UserInterestProfile.searchWeights(many, NOW).size());
    }

    @Test
    @DisplayName("Chỉ có tìm kiếm (chưa tương tác bài nào) vẫn dựng được hồ sơ khi đủ 3 câu")
    void searchesAloneBuildProfile() {
        List<UserInterestProfile.WeightedVector> two = UserInterestProfile.searchWeights(List.of(
                new UserInterestProfile.Search("a", axis(0), NOW), new UserInterestProfile.Search("b", axis(0), NOW)), NOW);
        assertTrue(UserInterestProfile.centroid(List.of(), Map.of(), two).isEmpty(), "2 câu là chưa đủ");

        List<UserInterestProfile.WeightedVector> three = UserInterestProfile.searchWeights(List.of(
                new UserInterestProfile.Search("a", axis(0), NOW), new UserInterestProfile.Search("b", axis(0), NOW),
                new UserInterestProfile.Search("c", axis(0), NOW)), NOW);
        Optional<float[]> profile = UserInterestProfile.centroid(List.of(), Map.of(), three);
        assertTrue(profile.isPresent());
        assertEquals(1f, profile.get()[0], 1e-6);
    }

    @Test
    @DisplayName("Tìm kiếm kéo vector sở thích về phía chủ đề vừa tìm")
    void searchPullsProfile() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        List<UserInterestProfile.Weighted> items = List.of(
                new UserInterestProfile.Weighted(p1, 1), new UserInterestProfile.Weighted(p2, 1), new UserInterestProfile.Weighted(p3, 1));
        Map<UUID, float[]> emb = Map.of(p1, axis(0), p2, axis(0), p3, axis(0));

        float[] without = UserInterestProfile.centroid(items, emb).orElseThrow();
        float[] with = UserInterestProfile.centroid(items, emb, UserInterestProfile.searchWeights(
                List.of(new UserInterestProfile.Search("bóng đá", axis(1), NOW)), NOW)).orElseThrow();

        assertEquals(0f, without[1], 1e-6);
        assertTrue(with[1] > 0.2f, "thành phần của chủ đề vừa tìm phải tăng, thực tế " + with[1]);
        assertTrue(with[0] > with[1], "3 bài đã thích vẫn nặng hơn 1 câu tìm");
    }
}
