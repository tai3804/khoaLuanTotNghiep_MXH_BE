package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Item;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Mode;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Signals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostRelevanceScorerTest {

    private static Signals post(double ageHours, long likes, long comments, long shares,
                                boolean friend, boolean following, boolean group, int interactions) {
        return new Signals(ageHours, likes, comments, shares, false, 50, false, friend, following, group, interactions, false);
    }

    private static double relevance(Signals s) {
        return PostRelevanceScorer.score(Mode.RELEVANCE, s).score();
    }

    @Test
    void friendsPostStaysAheadOfFreshButUnpopularStrangerPost() {
        double friendDayOld = relevance(post(20, 0, 0, 0, true, false, false, 0));
        double strangerFresh = relevance(post(2, 5, 0, 0, false, false, false, 0));
        assertTrue(friendDayOld > strangerFresh, "bài của bạn bè dù hơn 20 giờ vẫn nên đứng trên bài lạ ít tương tác");
    }

    @Test
    void aViralStrangerPostCanBeatAnOldFriendPost() {
        double friendDayOld = relevance(post(20, 0, 0, 0, true, false, false, 0));
        double viral = relevance(post(2, 100, 20, 10, false, false, false, 0));
        assertTrue(viral > friendDayOld, "bài lạ đang rất nổi phải chen được lên trên bài cũ của bạn bè");
    }

    @Test
    void oldPostsFadeEvenFromFriends() {
        double friendFresh = relevance(post(1, 0, 0, 0, true, false, false, 0));
        double friendFiveDays = relevance(post(120, 0, 0, 0, true, false, false, 0));
        double strangerFreshSmall = relevance(post(2, 5, 0, 0, false, false, false, 0));
        assertTrue(friendFresh > friendFiveDays);
        assertTrue(strangerFreshSmall > friendFiveDays, "bài của bạn bè sau 5 ngày không còn đáng đứng trên bài mới");
    }

    @Test
    void sameAffinityFresherPostWins() {
        assertTrue(relevance(post(1, 3, 0, 0, true, false, false, 0)) > relevance(post(48, 3, 0, 0, true, false, false, 0)));
    }

    @Test
    void pastInteractionsRaiseAStrangersPosts() {
        double stranger = relevance(post(5, 2, 0, 0, false, false, false, 0));
        double interacted = relevance(post(5, 2, 0, 0, false, false, false, 10));
        assertTrue(interacted > stranger);
        assertEquals("interacted", PostRelevanceScorer.score(Mode.RELEVANCE, post(5, 2, 0, 0, false, false, false, 10)).reason());
    }

    @Test
    void friendOutranksFollowingOutranksStranger() {
        double friend = relevance(post(5, 2, 0, 0, true, false, false, 0));
        double following = relevance(post(5, 2, 0, 0, false, true, false, 0));
        double stranger = relevance(post(5, 2, 0, 0, false, false, false, 0));
        assertTrue(friend > following && following > stranger);
    }

    @Test
    void postsInJoinedGroupsGetABoost() {
        assertTrue(relevance(post(5, 2, 0, 0, false, false, true, 0)) > relevance(post(5, 2, 0, 0, false, false, false, 0)));
    }

    @Test
    void commentsAndSharesCountMoreThanLikes() {
        assertTrue(PostRelevanceScorer.engagement(0, 10, 0) > PostRelevanceScorer.engagement(10, 0, 0));
        assertTrue(PostRelevanceScorer.engagement(0, 0, 10) > PostRelevanceScorer.engagement(0, 10, 0));
    }

    @Test
    void trendingFavoursInteractionVelocity() {
        double fastRising = PostRelevanceScorer.score(Mode.TRENDING, post(3, 40, 5, 2, false, false, false, 0)).score();
        double slowOldSameCount = PostRelevanceScorer.score(Mode.TRENDING, post(48, 40, 5, 2, false, false, false, 0)).score();
        assertTrue(fastRising > slowOldSameCount);
        assertEquals("trending", PostRelevanceScorer.score(Mode.TRENDING, post(3, 40, 5, 2, false, false, false, 0)).reason());
    }

    @Test
    void diversifyKeepsOnePersonFromTakingOverTheFeed() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Item> items = List.of(
                new Item(UUID.randomUUID(), a, 10.0, "friend"),
                new Item(UUID.randomUUID(), a, 9.5, "friend"),
                new Item(UUID.randomUUID(), a, 9.0, "friend"),
                new Item(UUID.randomUUID(), a, 8.5, "friend"),
                new Item(UUID.randomUUID(), b, 8.0, "friend"));

        List<Item> result = PostRelevanceScorer.diversify(items, 5);
        assertEquals(5, result.size());
        assertEquals(a, result.get(0).authorId());
        assertEquals(b, result.get(1).authorId(), "bài của người khác phải chen lên trước bài thứ hai của cùng một tác giả");
    }

    @Test
    void diversifyKeepsOrderWhenAuthorsAlreadyDiffer() {
        List<Item> items = List.of(
                new Item(UUID.randomUUID(), UUID.randomUUID(), 5.0, "recent"),
                new Item(UUID.randomUUID(), UUID.randomUUID(), 4.0, "recent"),
                new Item(UUID.randomUUID(), UUID.randomUUID(), 3.0, "recent"));
        List<Item> result = PostRelevanceScorer.diversify(items, 3);
        assertEquals(items, result);
    }

    @Test
    void diversifyRespectsTheLimit() {
        UUID a = UUID.randomUUID();
        List<Item> items = List.of(new Item(UUID.randomUUID(), a, 3, "x"), new Item(UUID.randomUUID(), a, 2, "x"), new Item(UUID.randomUUID(), a, 1, "x"));
        assertEquals(2, PostRelevanceScorer.diversify(items, 2).size());
    }

    @Test
    void myFreshPostStaysNearTheTop() {
        Signals freshOwn = new Signals(0.2, 0, 0, 0, false, 50, true, false, false, false, 0, false);
        Signals oldOwn = new Signals(30, 0, 0, 0, false, 50, true, false, false, false, 0, false);
        double friendFresh = relevance(post(0.5, 0, 0, 0, true, false, false, 0));
        double strangerFresh = relevance(post(0.5, 3, 0, 0, false, false, false, 0));
        assertTrue(relevance(freshOwn) > relevance(oldOwn), "bài mình vừa đăng phải được ưu tiên hơn bài của mình đã cũ");
        assertTrue(relevance(freshOwn) > strangerFresh, "bài mình vừa đăng phải đứng trên bài lạ mới đăng");
        assertTrue(friendFresh > relevance(freshOwn), "nhưng vẫn không vượt bài mới của bạn bè");
    }
}
