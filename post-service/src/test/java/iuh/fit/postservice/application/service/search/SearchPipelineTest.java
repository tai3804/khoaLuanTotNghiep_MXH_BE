package iuh.fit.postservice.application.service.search;

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
    void parseSplitsHashtagsAndTerms() {
        ParsedQuery q = SearchTextAnalyzer.parse("#Java Lập trình web ");
        assertEquals(List.of("java"), q.hashtags());
        assertEquals(List.of("java", "lap", "trinh", "web"), q.terms());
        assertFalse(q.prefixLast(), "truy vấn kết thúc bằng khoảng trắng: người dùng đã gõ xong, không khớp tiền tố");
    }

    @Test
    void lastTermIsPrefixMatchedWhileUserIsStillTyping() {
        assertTrue(SearchTextAnalyzer.parse("lap tri").prefixLast());
    }

    @Test
    void stopwordsAreDroppedOnlyWhenOtherTermsRemain() {
        assertEquals(List.of("hoc", "lap", "trinh"), SearchTextAnalyzer.parse("học là lập trình").terms());
        assertEquals(List.of("on", "thi"), SearchTextAnalyzer.parse("ôn thi").terms(), "\"thi\" (kỳ thi) không được nhầm với \"thì\"");
        assertEquals(List.of("on"), SearchTextAnalyzer.parse("ôn thì").terms(), "\"thì\" có dấu mới là từ chức năng");
        assertEquals(List.of("the", "thao"), SearchTextAnalyzer.parse("thể thao").terms(), "\"thể\" không phải stopword");
        assertEquals(List.of("la", "va"), SearchTextAnalyzer.parse("là và").terms(), "truy vấn toàn từ chức năng không được rỗng");
    }

    @Test
    void repeatedTermsAndLongQueriesAreBounded() {
        assertEquals(List.of("java"), SearchTextAnalyzer.parse("java java JAVA").terms());
        String many = String.join(" ", java.util.stream.IntStream.range(0, 40).mapToObj(i -> "tu" + i).toList());
        assertEquals(12, SearchTextAnalyzer.parse(many).terms().size());
    }

    @Test
    void relativeCutoffKeepsOnlyResultsCloseToTheBestOne() {
        assertEquals(3, SearchScorer.cutoffIndex(new double[]{0.80, 0.50, 0.30, 0.20, 0.05}, 0.35));
        assertEquals(1, SearchScorer.cutoffIndex(new double[]{0.40, 0.11, 0.10}, 0.35), "đuôi toàn bài chỉ 'hơi giống' bị cắt");
        assertEquals(0, SearchScorer.cutoffIndex(new double[]{}, 0.35));
        assertEquals(2, SearchScorer.cutoffIndex(new double[]{0.05, 0.04}, 0.35), "thang điểm thấp vẫn trả kết quả, vì so với bài tốt nhất");
    }

    // ---------- BM25 ----------

    @Test
    void idfIsHigherForRareTermsAndNeverNegative() {
        assertTrue(Bm25.idf(1000, 3) > Bm25.idf(1000, 500));
        assertTrue(Bm25.idf(1000, 1000) > 0, "biến thể Lucene luôn dương kể cả khi từ có trong mọi tài liệu");
        assertEquals(Math.log(1 + 90.5 / 10.5), Bm25.idf(100, 10), 1e-12);
    }

    @Test
    void termFrequencySaturatesAndLongDocumentsArePenalised() {
        double idf = 2.0;
        double tf1 = Bm25.termScore(idf, 1, 20, 20);
        double tf5 = Bm25.termScore(idf, 5, 20, 20);
        double tf50 = Bm25.termScore(idf, 50, 20, 20);
        assertTrue(tf5 > tf1 && tf50 > tf5);
        assertTrue(tf50 < idf * (Bm25.K1 + 1), "điểm bão hoà, tiệm cận idf·(k1+1)");
        assertTrue(Bm25.termScore(idf, 2, 20, 20) > Bm25.termScore(idf, 2, 200, 20), "cùng tf, tài liệu dài hơn điểm thấp hơn");
        assertEquals(idf, Bm25.termScore(idf, 1, 20, 20), 1e-12, "tf=1, độ dài = trung bình thì điểm đúng bằng idf");
        assertEquals(0.0, Bm25.termScore(idf, 0, 20, 20));
    }

    private static double bm25(String doc, ParsedQuery q, List<Double> idfs, double avg) {
        return Bm25.score(SearchTextAnalyzer.tokens(doc), SearchTextAnalyzer.exactTokens(doc),
                q.terms(), q.exactTerms(), idfs, q.prefixLast(), avg);
    }

    @Test
    void bm25PrefersDocumentsMatchingMoreOfTheRareTerms() {
        ParsedQuery q = SearchTextAnalyzer.parse("spring kafka ");
        List<Double> idfs = List.of(Bm25.idf(1000, 300), Bm25.idf(1000, 10));
        double both = bm25("spring boot voi kafka", q, idfs, 8);
        double commonOnly = bm25("spring spring spring boot", q, idfs, 8);
        double none = bm25("hom nay troi dep", q, idfs, 8);
        assertTrue(both > commonOnly, "khớp từ hiếm (kafka) phải đáng giá hơn lặp từ phổ biến");
        assertEquals(0.0, none);
    }

    @Test
    void accentedQueryTermsMatchExactlyButUnaccentedOnesMatchFolded() {
        List<Double> idfs = List.of(2.0);
        ParsedQuery accented = SearchTextAnalyzer.parse("ôn ");
        assertTrue(bm25("mình đang ôn bài", accented, idfs, 4) > 0);
        assertEquals(0.0, bm25("Hôm nay thấy ổn hơn, cảm ơn bạn", accented, idfs, 6),
                "gõ \"ôn\" có dấu thì \"ổn\", \"ơn\" không được khớp");
        ParsedQuery plain = SearchTextAnalyzer.parse("on ");
        assertTrue(bm25("Hôm nay thấy ổn hơn", plain, idfs, 5) > 0, "gõ không dấu thì chấp nhận mọi biến thể dấu");
    }

    @Test
    void normalisedBm25IsAbsoluteSoAWeakMatchOnACommonTermStaysLow() {
        List<Double> idfs = List.of(Bm25.idf(1000, 900), Bm25.idf(1000, 5), Bm25.idf(1000, 8));
        double onlyCommon = Bm25.normalize(Bm25.termScore(idfs.get(0), 1, 8, 8), idfs);
        double allTerms = Bm25.normalize(
                Bm25.termScore(idfs.get(0), 1, 8, 8) + Bm25.termScore(idfs.get(1), 1, 8, 8) + Bm25.termScore(idfs.get(2), 1, 8, 8), idfs);
        assertTrue(onlyCommon < 0.05, "chỉ khớp từ gần như có ở mọi bài thì gần như không đáng kể");
        assertEquals(1.0, allTerms, 1e-9);
    }

    @Test
    void prefixMatchingAppliesOnlyToTheLastTerm() {
        List<String> tokens = SearchTextAnalyzer.tokens("lap trinh java");
        assertEquals(1, Bm25.termFrequency(tokens, "tri", true));
        assertEquals(0, Bm25.termFrequency(tokens, "tri", false));
    }

    // ---------- Reciprocal Rank Fusion ----------

    @Test
    void rrfRewardsDocumentsFoundByMoreThanOneRetriever() {
        UUID both = UUID.randomUUID();
        UUID lexicalOnly = UUID.randomUUID();
        UUID vectorOnly = UUID.randomUUID();
        Map<UUID, Double> fused = RankFusion.reciprocalRank(List.of(
                List.of(lexicalOnly, both),
                List.of(vectorOnly, both)));
        assertEquals(1.0 / 61, fused.get(lexicalOnly), 1e-12);
        assertEquals(2.0 / 62, fused.get(both), 1e-12);
        assertTrue(fused.get(both) > fused.get(lexicalOnly), "xuất hiện ở hai danh sách thắng đứng đầu một danh sách");
    }

    // ---------- SearchScorer ----------

    private static SearchScorer.Features f(double bm25, double cos, double phrase, double tag, double social, double eng, double fresh) {
        return new SearchScorer.Features(bm25, cos, phrase, tag, social, eng, fresh);
    }

    @Test
    void weightsOfEachGroupSumToOne() {
        assertEquals(1.0, SearchScorer.W_BM25 + SearchScorer.W_SEMANTIC + SearchScorer.W_PHRASE + SearchScorer.W_HASHTAG, 1e-12);
        assertEquals(1.0, SearchScorer.W_SOCIAL + SearchScorer.W_ENGAGEMENT + SearchScorer.W_FRESHNESS, 1e-12);
    }

    @Test
    void relevanceIsTheGateAnIrrelevantFriendPostCannotBeatARelevantStrangerPost() {
        double relevantStranger = SearchScorer.score(f(0.9, 5.0, 1.0, 0, 0.0, 0.0, 0.1), 1.0);
        double irrelevantFriend = SearchScorer.score(f(0.1, 0.0, 0, 0, 1.0, 1.0, 1.0), 1.0);
        assertTrue(relevantStranger > irrelevantFriend);
    }

    @Test
    void amongEquallyRelevantPostsFriendsAndPopularFreshPostsWin() {
        double base = SearchScorer.score(f(0.8, 4.0, 0.6, 0, 0.0, 0.1, 0.2), 1.0);
        double friend = SearchScorer.score(f(0.8, 4.0, 0.6, 0, SearchScorer.social(false, true, false, false, 0), 0.1, 0.2), 1.0);
        double popular = SearchScorer.score(f(0.8, 4.0, 0.6, 0, 0.0, SearchScorer.engagement(100, 20, 10), 0.2), 1.0);
        assertTrue(friend > base);
        assertTrue(popular > base);
    }

    @Test
    void lowReputationAuthorsAreDownRankedWithoutBeingRemoved() {
        double normal = SearchScorer.score(f(0.8, 4.0, 0.6, 0, 0, 0, 0), 1.0);
        double penalised = SearchScorer.score(f(0.8, 4.0, 0.6, 0, 0, 0, 0), 0.5);
        assertEquals(normal * 0.5, penalised, 1e-12);
        assertTrue(penalised > 0);
    }

    @Test
    void relevanceGateAdmitsKeywordHitsHashtagsAndStrongSemanticMatchesOnly() {
        assertTrue(SearchScorer.isRelevant(1.2, 0.0, 0));
        assertTrue(SearchScorer.isRelevant(0, 0.0, 1.0));
        assertTrue(SearchScorer.isRelevant(0, 3.5, 0));
        assertFalse(SearchScorer.isRelevant(0, 2.4, 0), "chỉ hơi gần nghĩa và không khớp từ khoá thì không đủ vào kết quả");
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
        // chỉ khớp 10% độ mạnh từ khoá (vài âm tiết phổ biến), không gần nghĩa: dưới sàn
        assertTrue(SearchScorer.relevance(f(0.10, 0.0, 0, 0, 0, 0, 0)) < SearchScorer.MIN_RELEVANCE);
        // khớp một nửa từ khoá: trên sàn
        assertTrue(SearchScorer.relevance(f(0.50, 0.0, 0, 0, 0, 0, 0)) > SearchScorer.MIN_RELEVANCE);
        // gần nghĩa vừa đủ qua ngưỡng z: trên sàn
        assertTrue(SearchScorer.relevance(f(0.0, SearchScorer.SEMANTIC_ONLY_MIN_Z, 0, 0, 0, 0, 0)) > SearchScorer.MIN_RELEVANCE);
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
        String exact = "Hôm nay mình học cả buổi ở thư viện";
        String folded = "Mình mua ca buoi sang o cho";
        assertEquals(SearchScorer.PHRASE_EXACT, PostSearchService.phraseMatch(q, exact, SearchTextAnalyzer.tokens(exact)));
        assertEquals(SearchScorer.PHRASE_FOLDED, PostSearchService.phraseMatch(q, folded, SearchTextAnalyzer.tokens(folded)));
        String unrelated = "cá hồi nướng";
        assertEquals(0.0, PostSearchService.phraseMatch(q, unrelated, SearchTextAnalyzer.tokens(unrelated)));
    }

    @Test
    void tsQueryUsesOrAndPrefixOnTheLastTermOnly() {
        assertEquals("lap | tri:*", iuh.fit.postservice.infrastructure.persistence.search.PostLexicalIndex
                .toTsQuery(List.of("lap", "tri"), true));
        assertEquals("lap | tri", iuh.fit.postservice.infrastructure.persistence.search.PostLexicalIndex
                .toTsQuery(List.of("lap", "tri"), false));
    }
}
