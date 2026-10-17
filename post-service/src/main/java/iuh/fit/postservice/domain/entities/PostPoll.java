package iuh.fit.postservice.domain.entities;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPoll implements Serializable {
    private String question;
    @Builder.Default
    private List<PollOption> options = new ArrayList<>();
    private boolean multipleChoice;
    private boolean closed;
    private Instant closedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollOption implements Serializable {
        private String id;
        private String text;
        private long voteCount;
        @Builder.Default
        private Set<UUID> voterIds = new HashSet<>();
    }
}
