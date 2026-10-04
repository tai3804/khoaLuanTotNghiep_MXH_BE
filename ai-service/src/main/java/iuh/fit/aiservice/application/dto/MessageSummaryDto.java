package iuh.fit.aiservice.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class MessageSummaryDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private String senderName;
        private String text;
        private String time;
        private String mediaUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummarizeMessagesRequest {
        private String conversationName;
        private Boolean isGroup;
        private List<MessageItem> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummarizeMessagesResponse {
        private String summary;
        private String mediaDescription;
        private List<String> actionItems;
        private int messageCount;
        private int imageCount;
    }
}
