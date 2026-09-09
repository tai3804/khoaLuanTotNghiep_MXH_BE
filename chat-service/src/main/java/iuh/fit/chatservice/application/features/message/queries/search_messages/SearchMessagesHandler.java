package iuh.fit.chatservice.application.features.message.queries.search_messages;

import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import iuh.fit.chatservice.application.mapper.MessageFeatureMapper;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.entities.MessageReaction;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageReactionRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageUserDeletionRepository;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchMessagesHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageReactionRepository messageReactionRepository;
    MessageUserDeletionRepository messageUserDeletionRepository;
    MessageFeatureMapper messageFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetMessagesResult> handle(SearchMessagesQuery query) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(query.getConversationId(), query.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize());
        List<UUID> userDeletedIds = messageUserDeletionRepository.findAllDeletedMessageIdsByUserId(query.getCurrentUserId());

        Page<Message> messagePage = messageRepository.searchMessagesInConversation(
                query.getConversationId(),
                query.getKeyword() != null ? query.getKeyword() : "",
                userDeletedIds.isEmpty() ? null : userDeletedIds,
                pageRequest
        );

        List<Message> pageMessages = messagePage.getContent();
        List<UUID> messageIds = pageMessages.stream().map(Message::getId).toList();

        // Fetch reply messages
        List<UUID> replyIds = pageMessages.stream()
                .map(Message::getReplyToMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Message> replyMap = replyIds.isEmpty() ? Map.of() :
                messageRepository.findAllByIdIn(replyIds).stream()
                        .collect(Collectors.toMap(Message::getId, m -> m));

        // Fetch reactions
        List<MessageReaction> allReactions = messageIds.isEmpty() ? List.of() :
                messageReactionRepository.findByMessageIdIn(messageIds);

        Map<UUID, List<MessageReaction>> reactionMap = allReactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        List<GetMessagesResult> results = pageMessages.stream().map(message -> {
            GetMessagesResult result = messageFeatureMapper.toGetMessagesResult(message);

            if (message.getReplyToMessageId() != null && replyMap.containsKey(message.getReplyToMessageId())) {
                Message replyMsg = replyMap.get(message.getReplyToMessageId());
                result.setReplyToContent(replyMsg.getContent());
                result.setReplyToSenderId(replyMsg.getSenderId());
            }

            List<MessageReaction> reactions = reactionMap.getOrDefault(message.getId(), List.of());
            Map<String, Long> countMap = reactions.stream()
                    .collect(Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting()));
            result.setReactionsCount(countMap);

            if (query.getCurrentUserId() != null) {
                reactions.stream()
                        .filter(r -> r.getUserId().equals(query.getCurrentUserId()))
                        .findFirst()
                        .ifPresent(r -> result.setCurrentUserReaction(r.getEmoji()));
            }

            return result;
        }).toList();

        return PagedResponse.<GetMessagesResult>builder()
                .content(results)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .last(messagePage.isLast())
                .build();
    }
}
