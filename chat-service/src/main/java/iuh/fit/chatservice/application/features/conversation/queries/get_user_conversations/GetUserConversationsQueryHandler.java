package iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations;

import iuh.fit.chatservice.presentation.dto.response.UserPresenceResponse;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.chatservice.application.mapper.ConversationFeatureMapper;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.enums.ConversationType;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserConversationsQueryHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    ConversationFeatureMapper conversationFeatureMapper;
    iuh.fit.chatservice.application.service.UserPresenceService userPresenceService;

    @Transactional(readOnly = true)
    public PagedResponse<GetUserConversationsResult> handle(GetUserConversationsQuery query) {
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize());
        Page<ConversationMember> memberPage = conversationMemberRepository.findByUserIdAndStatus(
                query.getCurrentUserId(), MemberStatus.ACTIVE, pageRequest
        );

        List<ConversationMember> members = memberPage.getContent();
        List<UUID> conversationIds = members.stream().map(ConversationMember::getConversationId).toList();

        // Tải theo lô: mỗi loại dữ liệu đúng 1 truy vấn cho cả trang thay vì 3-4 truy vấn cho mỗi hội thoại (N+1)
        Map<UUID, Conversation> conversations = conversationIds.isEmpty() ? Map.of()
                : conversationRepository.findAllById(conversationIds).stream()
                        .collect(Collectors.toMap(Conversation::getId, c -> c, (x, y) -> x));

        Map<UUID, Long> unreadByConversation = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            for (Object[] row : messageRepository.countUnreadByConversationIds(query.getCurrentUserId(), conversationIds)) {
                unreadByConversation.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        List<UUID> directIds = conversations.values().stream()
                .filter(c -> !c.isDeleted() && c.getType() == ConversationType.DIRECT)
                .map(Conversation::getId).toList();
        Map<UUID, UUID> otherParticipantByConversation = new HashMap<>();
        if (!directIds.isEmpty()) {
            for (ConversationMember m : conversationMemberRepository.findByConversationIdInAndStatus(directIds, MemberStatus.ACTIVE)) {
                if (!m.getUserId().equals(query.getCurrentUserId())) {
                    otherParticipantByConversation.putIfAbsent(m.getConversationId(), m.getUserId());
                }
            }
        }
        // Trạng thái trực tuyến của mọi người đối thoại: một lần multiGet trên Redis
        Map<UUID, UserPresenceResponse> presenceByUser = otherParticipantByConversation.isEmpty() ? Map.of()
                : userPresenceService.getBatchPresence(new ArrayList<>(new HashSet<>(otherParticipantByConversation.values())));

        List<GetUserConversationsResult> contentList = new ArrayList<>();
        for (ConversationMember member : members) {
            Conversation conversation = conversations.get(member.getConversationId());
            if (conversation == null || conversation.isDeleted()) {
                continue;
            }
            GetUserConversationsResult result = conversationFeatureMapper.toUserConversationsResult(conversation);
            result.setUnreadCount(unreadByConversation.getOrDefault(conversation.getId(), 0L));

            UUID otherId = otherParticipantByConversation.get(conversation.getId());
            if (otherId != null) {
                result.setOtherParticipantId(otherId);
                UserPresenceResponse presence = presenceByUser.get(otherId);
                if (presence != null) {
                    result.setIsOnline(presence.isOnline());
                    result.setOtherLastActiveAt(presence.getLastActiveAt());
                }
            }
            contentList.add(result);
        }

        return PagedResponse.<GetUserConversationsResult>builder()
                .content(contentList)
                .page(memberPage.getNumber())
                .size(memberPage.getSize())
                .totalElements(memberPage.getTotalElements())
                .totalPages(memberPage.getTotalPages())
                .last(memberPage.isLast())
                .build();
    }
}
