package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    List<GroupMember> findAllByUserIdAndStatus(UUID userId, GroupMemberStatus status);
    List<GroupMember> findAllByGroupIdAndStatus(UUID groupId, GroupMemberStatus status);

    List<GroupMember> findAllByGroupId(UUID groupId);
    List<GroupMember> findAllByGroupIdInAndStatus(java.util.Collection<UUID> groupIds, GroupMemberStatus status);

    /** Quan hệ của một người dùng với nhiều nhóm trong một truy vấn (tránh N+1 khi liệt kê nhóm). */
    List<GroupMember> findAllByUserIdAndGroupIdIn(UUID userId, java.util.Collection<UUID> groupIds);

    long countByGroupIdAndStatus(UUID groupId, GroupMemberStatus status);
}

