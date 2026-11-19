package iuh.fit.userservice.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextTest {

    @Test
    void foldRemovesVietnameseMarksAndCase() {
        assertEquals("tran thanh tai", SearchText.fold("Trần Thành  Tài"));
        assertEquals("do duc dung", SearchText.fold("  Đỗ Đức Dũng "));
        assertEquals("", SearchText.fold(null));
    }

    @Test
    void likePatternFoldsAndEscapesWildcards() {
        assertEquals("%tai%", SearchText.likePattern("Tài"));
        assertEquals("%100!% !_ vui!!%", SearchText.likePattern("100% _ vui!"));
        assertEquals("%%", SearchText.likePattern(null));
    }

    @Test
    void likePatternCapsVeryLongKeywords() {
        String pattern = SearchText.likePattern("a".repeat(500));
        assertEquals(100 + 2, pattern.length());
    }

    @Test
    void nameRankPrefersExactThenWholeWordThenPrefixThenInside() {
        String q = SearchText.fold("Tài");
        int exact = SearchText.nameRank("tai", q);
        int wholeWord = SearchText.nameRank("tran thanh tai", q);
        int wordPrefix = SearchText.nameRank("nguyen tai loc", SearchText.fold("tai"));
        int prefix = SearchText.nameRank("tai loc", SearchText.fold("ta"));
        int inside = SearchText.nameRank("nguyen thanh phat", SearchText.fold("han"));

        assertEquals(0, exact);
        assertEquals(1, wholeWord);
        assertEquals(1, wordPrefix); // "tai" là nguyên từ trong "nguyen tai loc"
        assertEquals(2, prefix);
        assertEquals(3, inside);
        assertTrue(exact < wholeWord && wholeWord < prefix && prefix < inside);
    }

    @Test
    void nameRankMatchesMultiWordPhrases() {
        assertEquals(1, SearchText.nameRank("tran thanh tai", "thanh tai"));
        assertEquals(2, SearchText.nameRank("tran thanh tai", "thanh ta"));
    }
}
