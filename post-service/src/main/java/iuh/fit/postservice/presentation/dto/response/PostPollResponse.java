package iuh.fit.postservice.presentation.dto.response;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPollResponse implements Serializable {
    private String question;
    private List<PollOptionResponse> options;
    private long totalVotes;
    private boolean multipleChoice;
    private boolean closed;
    private Instant closedAt;
    private boolean userVoted;
    private List<String> userVotedOptionIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollOptionResponse implements Serializable {
        private String id;
        private String text;
        private long voteCount;
        private double votePercentage;
        private boolean votedByCurrentUser;
    }
}
