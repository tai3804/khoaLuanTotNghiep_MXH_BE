package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LiveNotificationPublisherTest {

    private static final String TOPIC = "notification.in-app.send";

    private UserConnectionClient userConnectionClient;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private LiveNotificationPublisher publisher;

    private final UUID authorId = UUID.randomUUID();
    private final UUID friend1 = UUID.randomUUID();
    private final UUID friend2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userConnectionClient = mock(UserConnectionClient.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new LiveNotificationPublisher(userConnectionClient, kafkaTemplate);
        ApiResponse<Map<String, List<UUID>>> graph = mock(ApiResponse.class);
        when(graph.getData()).thenReturn(Map.of("friendIds", List.of(friend1, friend2, authorId), "followingIds", List.of()));
        when(userConnectionClient.getSocialGraph()).thenReturn(graph);
    }

    private Post livePost(String content, PostPrivacy privacy, UUID groupId) {
        return Post.builder().id(UUID.randomUUID()).authorId(authorId).groupId(groupId).content(content).privacy(privacy).build();
    }

    @Test
    void notifiesEveryFriendButNotTheHost() {
        Post post = livePost(ReputationGuard.LIVE_MARKER + " Trò chuyện cuối tuần\n\nMô tả dài", PostPrivacy.PUBLIC, null);

        publisher.liveStarted(post);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(2)).send(eq(TOPIC), event.capture());
        List<Map<String, Object>> sent = event.getAllValues().stream().map(e -> (Map<String, Object>) e).toList();
        assertThat(sent).extracting(e -> e.get("recipientId")).containsExactlyInAnyOrder(friend1.toString(), friend2.toString());
        Map<String, Object> first = sent.get(0);
        assertThat(first.get("type")).isEqualTo("LIVE_STARTED");
        assertThat(first.get("actorId")).isEqualTo(authorId.toString());
        assertThat(first.get("targetId")).isEqualTo(post.getId().toString());
        assertThat(first.get("targetUrl")).isEqualTo("/posts/" + post.getId());
        assertThat(first.get("content")).isEqualTo("Một người dùng đang phát trực tiếp: \"Trò chuyện cuối tuần\"");
    }

    @Test
    void friendsOnlyLiveIsAlsoAnnounced() {
        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Chỉ bạn bè", PostPrivacy.FRIENDS, null));
        verify(kafkaTemplate, times(2)).send(eq(TOPIC), any());
    }

    @Test
    void ordinaryPostsPrivateLivesAndGroupLivesAreNotAnnounced() {
        publisher.liveStarted(livePost("Một bài viết bình thường", PostPrivacy.PUBLIC, null));
        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Riêng tư", PostPrivacy.PRIVATE, null));
        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Người xem tùy chọn", PostPrivacy.CUSTOM, null));
        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Trong nhóm", PostPrivacy.PUBLIC, UUID.randomUUID()));
        publisher.liveStarted(null);

        verify(kafkaTemplate, never()).send(any(String.class), any());
        verify(userConnectionClient, never()).getSocialGraph();
    }

    @Test
    void quotesInTheTitleDoNotBreakTheNotificationText() {
        assertThat(LiveNotificationPublisher.liveTitle(ReputationGuard.LIVE_MARKER + " Nói \"không\" với ngu\nmô tả")).isEqualTo("Nói 'không' với ngu");
        String longTitle = "a".repeat(300);
        assertThat(LiveNotificationPublisher.liveTitle(ReputationGuard.LIVE_MARKER + " " + longTitle)).hasSize(101).endsWith("…");
        assertThat(LiveNotificationPublisher.liveTitle(ReputationGuard.LIVE_MARKER)).isEmpty();
    }

    @Test
    void failureToLoadFriendsNeverBreaksStartingTheLive() {
        when(userConnectionClient.getSocialGraph()).thenThrow(new RuntimeException("user-service down"));
        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Vẫn phát được", PostPrivacy.PUBLIC, null));
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void neverNotifiesMoreThanTheCap() {
        List<UUID> many = java.util.stream.IntStream.range(0, LiveNotificationPublisher.MAX_RECIPIENTS + 200)
                .mapToObj(i -> UUID.randomUUID()).toList();
        ApiResponse<Map<String, List<UUID>>> graph = mock(ApiResponse.class);
        when(graph.getData()).thenReturn(Map.of("friendIds", many));
        when(userConnectionClient.getSocialGraph()).thenReturn(graph);

        publisher.liveStarted(livePost(ReputationGuard.LIVE_MARKER + " Đông bạn bè", PostPrivacy.PUBLIC, null));

        verify(kafkaTemplate, times(LiveNotificationPublisher.MAX_RECIPIENTS)).send(eq(TOPIC), any());
    }
}
