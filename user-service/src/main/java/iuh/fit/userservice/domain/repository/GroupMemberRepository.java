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
}

