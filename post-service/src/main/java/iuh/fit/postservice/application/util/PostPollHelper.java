package iuh.fit.postservice.application.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.postservice.domain.entities.PostPoll;
import iuh.fit.postservice.presentation.dto.request.CreatePollRequest;
import iuh.fit.postservice.presentation.dto.response.PostPollResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

@Slf4j
public class PostPollHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String buildPollJson(CreatePollRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return null;
        }
        if (request.getOptions() == null || request.getOptions().size() < 2) {
            return null;
        }

        List<PostPoll.PollOption> options = new ArrayList<>();
        int index = 1;
        for (String optText : request.getOptions()) {
            if (optText != null && !optText.isBlank()) {
                options.add(PostPoll.PollOption.builder()
                        .id("opt_" + index++)
                        .text(optText.trim())
                        .voteCount(0)
                        .voterIds(new HashSet<>())
                        .build());
            }
        }

        if (options.size() < 2) {
            return null;
        }

        PostPoll poll = PostPoll.builder()
                .question(request.getQuestion().trim())
                .options(options)
                .multipleChoice(request.isMultipleChoice())
                .closed(false)
                .closedAt(request.getClosedAt())
                .build();

        try {
            return OBJECT_MAPPER.writeValueAsString(poll);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PostPoll: {}", e.getMessage());
            return null;
        }
    }

    public static PostPoll fromJson(String pollData) {
        if (pollData == null || pollData.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readValue(pollData, PostPoll.class);
        } catch (Exception e) {
            log.error("Failed to deserialize PostPoll: {}", e.getMessage());
            return null;
        }
    }

    public static String toJson(PostPoll poll) {
        if (poll == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(poll);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PostPoll: {}", e.getMessage());
            return null;
        }
    }

    public static PostPollResponse toResponse(String pollData, UUID currentUserId) {
        PostPoll poll = fromJson(pollData);
        if (poll == null) return null;

        boolean isExpired = poll.getClosedAt() != null && Instant.now().isAfter(poll.getClosedAt());
        boolean isClosed = poll.isClosed() || isExpired;

        long totalVotes = 0;
        List<String> userVotedOptionIds = new ArrayList<>();

        if (poll.getOptions() != null) {
            for (PostPoll.PollOption opt : poll.getOptions()) {
                long count = opt.getVoterIds() != null ? opt.getVoterIds().size() : opt.getVoteCount();
                totalVotes += count;
                if (currentUserId != null && opt.getVoterIds() != null && opt.getVoterIds().contains(currentUserId)) {
                    userVotedOptionIds.add(opt.getId());
                }
            }
        }

        List<PostPollResponse.PollOptionResponse> optionResponses = new ArrayList<>();
        if (poll.getOptions() != null) {
            for (PostPoll.PollOption opt : poll.getOptions()) {
                long count = opt.getVoterIds() != null ? opt.getVoterIds().size() : opt.getVoteCount();
                double percentage = totalVotes > 0 ? Math.round((count * 1000.0) / totalVotes) / 10.0 : 0.0;
                boolean votedByMe = currentUserId != null && opt.getVoterIds() != null && opt.getVoterIds().contains(currentUserId);

                optionResponses.add(PostPollResponse.PollOptionResponse.builder()
                        .id(opt.getId())
                        .text(opt.getText())
                        .voteCount(count)
                        .votePercentage(percentage)
                        .votedByCurrentUser(votedByMe)
                        .build());
            }
        }

        return PostPollResponse.builder()
                .question(poll.getQuestion())
                .options(optionResponses)
                .totalVotes(totalVotes)
                .multipleChoice(poll.isMultipleChoice())
                .closed(isClosed)
                .closedAt(poll.getClosedAt())
                .userVoted(!userVotedOptionIds.isEmpty())
                .userVotedOptionIds(userVotedOptionIds)
                .build();
    }
}
