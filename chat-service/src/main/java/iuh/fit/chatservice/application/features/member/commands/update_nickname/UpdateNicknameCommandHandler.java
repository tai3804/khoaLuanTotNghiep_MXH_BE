package iuh.fit.chatservice.application.features.member.commands.update_nickname;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdateNicknameCommandHandler {

    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public void handle(UpdateNicknameCommand command) {
        boolean isCurrentUserMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isCurrentUserMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        ConversationMember targetMember = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getTargetUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        targetMember.setNickname(command.getNickname() != null ? command.getNickname().trim() : null);
        conversationMemberRepository.save(targetMember);
    }
}
