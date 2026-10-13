package iuh.fit.userservice.application.service.suggestion;

import iuh.fit.userservice.application.service.suggestion.FriendSuggestionScorer.Scored;
import iuh.fit.userservice.application.service.suggestion.FriendSuggestionScorer.Signals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendSuggestionScorerTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);

    private static Instant daysAgo(long days) {
        return NOW.minusDays(days).atZone(ZoneId.systemDefault()).toInstant();
    }


    /** Builder nhỏ gọn để mỗi bài test chỉ khai báo các tín hiệu nó quan tâm. */
    private static Signals signals(List<Integer> mutualDegrees, int groups, double groupWeight, boolean followsMe, boolean iFollow,
                                   String candidateLocation, Integer ageGap, boolean online, Instant lastActive,
                                   LocalDateTime createdAt, long friendCount) {
        return new Signals(mutualDegrees, groups, groupWeight, followsMe, iFollow, "Hà Nội", candidateLocation,
                ageGap, online, lastActive, createdAt, friendCount);
    }

    private static Scored score(Signals s) {
        return FriendSuggestionScorer.score(VIEWER, CANDIDATE, s, NOW);
    }

    @Test
    void socialSignalAlwaysBeatsProfileOnlySignals() {
        // Người "đẹp hồ sơ" nhất có thể: cùng nơi ở, cùng tuổi, đang online, thành viên mới, rất nhiều bạn
        Scored profileOnly = score(signals(null, 0, 0, false, false, "Hà Nội", 0, true, daysAgo(0), NOW.minusDays(1), 5000));
        // Người có tín hiệu xã hội YẾU nhất: một bạn chung mà người đó quen cả thế giới, hoặc một nhóm rất đông
        Scored weakMutual = score(signals(List.of(5000), 0, 0, false, false, null, null, false, null, null, 0));
        Scored weakGroup = score(signals(null, 1, 0.5, false, false, null, null, false, null, null, 0));
        assertTrue(weakMutual.score() > profileOnly.score(), "bạn chung phải xếp trên chỉ-có-hồ-sơ");
        assertTrue(weakGroup.score() > profileOnly.score(), "cùng nhóm phải xếp trên chỉ-có-hồ-sơ");
        assertTrue(profileOnly.score() < 5.0, "điểm tối đa chỉ-có-hồ-sơ phải dưới điểm cơ sở của tín hiệu xã hội");
        assertTrue(!profileOnly.hasSocialSignal() && weakMutual.hasSocialSignal());
    }

    @Test
    void adamicAdarFavorsSmallCirclesOverHubs() {
        Scored viaClosePerson = score(signals(List.of(3), 0, 0, false, false, null, null, false, null, null, 0));
        Scored viaHub = score(signals(List.of(3000), 0, 0, false, false, null, null, false, null, null, 0));
        assertTrue(viaClosePerson.score() > viaHub.score(), "bạn chung có vòng quan hệ nhỏ phải có giá trị hơn người quen tất cả");
    }

    @Test
    void moreMutualFriendsRankHigher() {
        Scored one = score(signals(List.of(10), 0, 0, false, false, null, null, false, null, null, 0));
        Scored three = score(signals(List.of(10, 10, 10), 0, 0, false, false, null, null, false, null, null, 0));
        assertTrue(three.score() > one.score());
        assertEquals(3, three.mutualCount());
        assertTrue(three.reasons().contains("3 bạn chung"));
    }

    @Test
    void followingMeBeatsFollowedByMe() {
        Scored followsMe = score(signals(null, 0, 0, true, false, null, null, false, null, null, 0));
        Scored iFollow = score(signals(null, 0, 0, false, true, null, null, false, null, null, 0));
        assertTrue(followsMe.score() > iFollow.score());
        assertTrue(followsMe.reasons().contains("Đang theo dõi bạn"));
        assertTrue(iFollow.reasons().contains("Bạn đang theo dõi"));
    }

    @Test
    void smallGroupsAreWorthMoreThanHugeOnes() {
        assertTrue(FriendSuggestionScorer.groupWeight(12) > FriendSuggestionScorer.groupWeight(80));
        assertTrue(FriendSuggestionScorer.groupWeight(80) > FriendSuggestionScorer.groupWeight(5000));
    }

    @Test
    void inactiveAccountsArePenalised() {
        Scored active = score(signals(List.of(10), 0, 0, false, false, null, null, false, daysAgo(2), NOW.minusDays(400), 10));
        Scored dormant = score(signals(List.of(10), 0, 0, false, false, null, null, false, daysAgo(200), NOW.minusDays(400), 10));
        assertTrue(active.score() > dormant.score());
    }

    @Test
    void scoringIsDeterministicAndJitterIsBounded() {
        Signals s = signals(List.of(10), 0, 0, false, false, null, null, false, daysAgo(2), NOW.minusDays(400), 10);
        assertEquals(score(s).score(), score(s).score(), 0.0, "cùng đầu vào phải cho cùng điểm");
        Scored tomorrow = FriendSuggestionScorer.score(VIEWER, CANDIDATE, s, NOW.plusDays(1));
        assertTrue(Math.abs(tomorrow.score() - score(s).score()) < FriendSuggestionScorer.JITTER_MAX + 0.6,
                "nhiễu theo ngày phải nhỏ so với khoảng cách giữa các mức tín hiệu");
    }

    @Test
    void reasonsDescribeWhyTheCandidateWasSuggested() {
        Scored s = score(signals(List.of(4, 8), 2, 3.0, true, false, "Hà Nội", 1, true, daysAgo(0), NOW.minusDays(3), 40));
        assertTrue(s.reasons().contains("2 bạn chung"));
        assertTrue(s.reasons().contains("Cùng 2 nhóm"));
        assertTrue(s.reasons().contains("Đang theo dõi bạn"));
        assertTrue(s.reasons().contains("Cùng sống tại Hà Nội"));
        assertTrue(s.reasons().contains("Đang hoạt động"));
        assertTrue(s.reasons().contains("Thành viên mới"));
    }
}
