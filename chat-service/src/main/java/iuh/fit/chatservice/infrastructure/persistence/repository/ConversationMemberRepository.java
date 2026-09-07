package iuh.fit.chatservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.enums.MemberRole;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends BaseJpaRepository<ConversationMember, UUID> {

    Optional<ConversationMember> findByConversationIdAndUserIdAndStatus(UUID conversationId, UUID userId, MemberStatus status);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationMember> findByConversationIdAndStatus(UUID conversationId, MemberStatus status);

    long countByConversationIdAndStatus(UUID conversationId, MemberStatus status);

    boolean existsByConversationIdAndUserIdAndStatus(UUID conversationId, UUID userId, MemberStatus status);

    @Query("""
        SELECT cm FROM ConversationMember cm
        WHERE cm.userId = :userId AND cm.status = :status
        ORDER BY cm.createdAt DESC
    """)
    Page<ConversationMember> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") MemberStatus status, Pageable pageable);

    @Query("""
        SELECT cm FROM ConversationMember cm
        WHERE cm.conversationId = :conversationId AND cm.status = 'ACTIVE'
        ORDER BY cm.joinedAt ASC
    """)
    List<ConversationMember> findActiveMembersOrderedByJoinedAt(@Param("conversationId") UUID conversationId);
}
