package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.search.SearchScorer.Weights;
import iuh.fit.postservice.application.service.search.SearchTextAnalyzer.ParsedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPipelineTest {

    // ---------- SearchTextAnalyzer ----------

    @Test
    void foldRemovesVietnameseDiacriticsAndMapsDStroke() {
        assertEquals("duong ca phe dep nhat", SearchTextAnalyzer.fold("Đường Cà Phê ĐẸP nhất"));
    }

    @Test
    void parseExtractsHashtagsInBothFoldedAndStoredForm() {
        ParsedQuery q = SearchTextAnalyzer.parse("#TiếngViệt Lập trình web ");
        assertEquals(List.of("tiengviet"), q.hashtags());
        assertEquals(List.of("tiếngviệt"), q.hashtagTags(), "dạng lưu trong CSDL: chữ thường, giữ dấu");
        assertEquals("lap trinh web", SearchTextAnalyzer.parse("Lập trình web").foldedPhrase());
        assertEquals("lập trình web", SearchTextAnalyzer.parse("Lập  trình  #web").exactPhrase().replace(" web", " web"));
    }

    @Test
    void emptyQueriesAreDetected() {
        assertTrue(SearchTextAnalyzer.parse("   ").isEmpty());
        assertTrue(SearchTextAnalyzer.parse(null).isEmpty());
        assertFalse(SearchTextAnalyzer.parse("#java").isEmpty());
    }

    @Test
    void longQueriesAreBounded() {
        String longQuery = "a".repeat(500);
        assertEquals(200, SearchTextAnalyzer.parse(longQuery).raw().length());
    }

    @Test
    void relativeCutoffKeepsOnlyResultsCloseToTheBestOne() {
        assertEquals(3, SearchScorer.cutoffIndex(new double[]{0.80, 0.50, 0.30, 0.20, 0.05}, 0.35));
        assertEquals(1, SearchScorer.cutoffIndex(new double[]{0.40, 0.11, 0.10}, 0.35), "đuôi toàn bài chỉ 'hơi giống' bị cắt");
        assertEquals(0, SearchScorer.cutoffIndex(new double[]{}, 0.35));
        assertEquals(2, SearchScorer.cutoffIndex(new double[]{0.05, 0.04}, 0.35), "thang điểm thấp vẫn trả kết quả, vì so với bài tốt nhất");
    }

    // ---------- Reciprocal Rank Fusion ----------

    @Test
    void rrfRewardsDocumentsFoundByMoreThanOneRetriever() {
        UUID both = UUID.randomUUID();
        UUID globalOnly = UUID.randomUUID();
        UUID socialOnly = UUID.randomUUID();
        Map<UUID, Double> fused = RankFusion.reciprocalRank(List.of(
                List.of(globalOnly, both),
                List.of(socialOnly, both)));
        assertEquals(1.0 / 61, fused.get(globalOnly), 1e-12);
        assertEquals(2.0 / 62, fused.get(both), 1e-12);
        assertTrue(fused.get(both) > fused.get(globalOnly), "xuất hiện ở hai danh sách thắng đứng đầu một danh sách");
    }

    // ---------- SearchScorer ----------

    private static SearchScorer.Features f(double z, double phrase, double tag, double social, double eng, double fresh) {
        return new SearchScorer.Features(z, phrase, tag, social, eng, fresh);
    }

    @Test
    void defaultWeightsAreNormalisedAndParseable() {
        Weights d = Weights.DEFAULT.normalized();
        assertEquals(1.0, d.semantic() + d.phrase() + d.hashtag(), 1e-12);
        assertEquals(1.0, d.social() + d.engagement() + d.freshness(), 1e-12);
        assertEquals(Weights.DEFAULT, Weights.parse(null));
        assertEquals(Weights.DEFAULT, Weights.parse("không phải số"));
        assertEquals(Weights.DEFAULT, Weights.parse("1,2,3"), "sai số lượng thì dùng mặc định");
        Weights round = Weights.parse(Weights.DEFAULT.toCsv());
        assertEquals(d.semantic(), round.semantic(), 1e-3);
        assertEquals(d.span(), round.span(), 1e-3);
    }

    @Test
    void relevanceIsTheGateAnIrrelevantFriendPostCannotBeatARelevantStrangerPost() {
        double relevantStranger = SearchScorer.score(f(5.0, 1.0, 0, 0.0, 0.0, 0.1), 1.0);
        double irrelevantFriend = SearchScorer.score(f(0.0, 0, 0, 1.0, 1.0, 1.0), 1.0);
        assertTrue(relevantStranger > irrelevantFriend);
    }

    @Test
    void amongEquallyRelevantPostsFriendsAndPopularFreshPostsWin() {
        double base = SearchScorer.score(f(4.0, 0.6, 0, 0.0, 0.1, 0.2), 1.0);
        double friend = SearchScorer.score(f(4.0, 0.6, 0, SearchScorer.social(false, true, false, false, 0), 0.1, 0.2), 1.0);
        double popular = SearchScorer.score(f(4.0, 0.6, 0, 0.0, SearchScorer.engagement(100, 20, 10), 0.2), 1.0);
        assertTrue(friend > base);
        assertTrue(popular > base);
    }

    @Test
    void zeroSpanIgnoresThePrior() {
        Weights noPrior = new Weights(0.7, 0.2, 0.1, 0.5, 0.3, 0.2, 0.0);
        double a = SearchScorer.score(f(4.0, 0.6, 0, 0.0, 0.0, 0.0), 1.0, noPrior);
        double b = SearchScorer.score(f(4.0, 0.6, 0, 1.0, 1.0, 1.0), 1.0, noPrior);
        assertEquals(a, b, 1e-12);
    }

    @Test
    void lowReputationAuthorsAreDownRankedWithoutBeingRemoved() {
        double normal = SearchScorer.score(f(4.0, 0.6, 0, 0, 0, 0), 1.0);
        double penalised = SearchScorer.score(f(4.0, 0.6, 0, 0, 0, 0), 0.5);
        assertEquals(normal * 0.5, penalised, 1e-12);
        assertTrue(penalised > 0);
    }

    @Test
    void relevanceGateAdmitsPhraseHashtagAndStrongSemanticMatchesOnly() {
        assertTrue(SearchScorer.isRelevant(1.0, 0.0, 0));
        assertTrue(SearchScorer.isRelevant(0, 0.0, 1.0));
        assertTrue(SearchScorer.isRelevant(0, 3.5, 0));
        assertFalse(SearchScorer.isRelevant(0, 2.4, 0), "chỉ hơi gần nghĩa và không khớp cụm/hashtag thì không đủ vào kết quả");
    }

    @Test
    void semanticScoreUsesTheQuerysOwnBaselineNotAFixedCosine() {
        // Cùng cosine 0,66 nhưng nền khác nhau: truy vấn có nền cao (mọi bài đều ~0,60) thì 0,66 chẳng có gì nổi bật,
        // truy vấn có nền thấp (~0,45) thì 0,66 là rất nổi bật.
        var highBaseline = new iuh.fit.postservice.application.service.PostEmbeddingService.QueryBaseline(0.60, 0.03);
        var lowBaseline = new iuh.fit.postservice.application.service.PostEmbeddingService.QueryBaseline(0.45, 0.03);
        assertEquals(2.0, highBaseline.zScore(0.66), 1e-9);
        assertEquals(7.0, lowBaseline.zScore(0.66), 1e-9);
        assertEquals(0.0, SearchScorer.semantic(highBaseline.zScore(0.66)), 1e-9);
        assertEquals(1.0, SearchScorer.semantic(lowBaseline.zScore(0.66)), 1e-9);
        assertFalse(SearchScorer.isRelevant(0, highBaseline.zScore(0.66), 0));
        assertTrue(SearchScorer.isRelevant(0, lowBaseline.zScore(0.66), 0));
    }

    @Test
    void absoluteRelevanceFloorDropsWeakMatchesEvenWhenNothingBetterExists() {
        assertTrue(SearchScorer.relevance(f(1.0, 0, 0, 0, 0, 0)) < SearchScorer.MIN_RELEVANCE);
        assertTrue(SearchScorer.relevance(f(SearchScorer.SEMANTIC_ONLY_MIN_Z, 0, 0, 0, 0, 0)) > SearchScorer.MIN_RELEVANCE);
        assertTrue(SearchScorer.relevance(f(0.0, 1.0, 0, 0, 0, 0)) > SearchScorer.MIN_RELEVANCE);
    }

    @Test
    void socialFeatureIsBoundedAndOrdered() {
        double friend = SearchScorer.social(false, true, false, false, 0);
        double following = SearchScorer.social(false, false, true, false, 0);
        double own = SearchScorer.social(true, false, false, false, 0);
        double stranger = SearchScorer.social(false, false, false, false, 0);
        double max = SearchScorer.social(false, true, false, true, 100_000);
        assertTrue(friend > following && following > own && own > stranger);
        assertTrue(max <= 1.0 && max > friend);
    }

    @Test
    void freshnessHalvesEveryThirtyDays() {
        assertEquals(1.0, SearchScorer.freshness(0), 1e-12);
        assertEquals(0.5, SearchScorer.freshness(24 * 30), 1e-12);
    }

    // ---------- phrase feature: accent-exact beats accent-folded (the "cả"/"cá" case) ----------

    @Test
    void exactPhraseWithDiacriticsOutranksFoldedOnlyMatch() {
        ParsedQuery q = SearchTextAnalyzer.parse("cả buổi");
        assertEquals(SearchScorer.PHRASE_EXACT, PostSearchService.phraseMatch(q, "Hôm nay mình học cả buổi ở thư viện"));
        assertEquals(SearchScorer.PHRASE_FOLDED, PostSearchService.phraseMatch(q, "Mình mua ca buoi sang o cho"));
        assertEquals(0.0, PostSearchService.phraseMatch(q, "cá hồi nướng"));
    }
}
