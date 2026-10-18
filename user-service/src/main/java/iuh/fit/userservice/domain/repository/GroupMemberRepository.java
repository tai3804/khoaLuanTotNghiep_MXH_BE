package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    List<GroupMember> findAllByUserIdAndStatus(UUID userId, GroupMemberStatus status);
    List<GroupMember> findAllByGroupIdAndStatus(UUID groupId, GroupMemberStatus status);

    List<GroupMember> findAllByGroupId(UUID groupId);
    List<GroupMember> findAllByGroupIdInAndStatus(Collection<UUID> groupIds, GroupMemberStatus status);

    /** Quan hệ của một người dùng với nhiều nhóm trong một truy vấn (tránh N+1 khi liệt kê nhóm). */
    List<GroupMember> findAllByUserIdAndGroupIdIn(UUID userId, Collection<UUID> groupIds);

    long countByGroupIdAndStatus(UUID groupId, GroupMemberStatus status);

    /** Số thành viên đã tham gia kể từ một mốc (thẻ "Hoạt động của nhóm"). */
    long countByGroupIdAndStatusAndJoinedAtAfter(UUID groupId, GroupMemberStatus status, java.time.LocalDateTime since);

    /** Quản trị viên và người kiểm duyệt đã được duyệt của nhóm. */
    List<GroupMember> findAllByGroupIdAndStatusAndRoleIn(UUID groupId, GroupMemberStatus status, Collection<GroupRole> roles);

    /** Thành viên đã chọn nhận thông báo ở mức cho trước (vd. ALL: thông báo mọi bài viết mới). */
    List<GroupMember> findAllByGroupIdAndStatusAndNotificationLevel(UUID groupId, GroupMemberStatus status, GroupNotificationLevel level);

    Page<GroupMember> findByGroupIdAndStatusAndRoleIn(UUID groupId, GroupMemberStatus status, Collection<GroupRole> roles, Pageable pageable);

    /** Như trên nhưng lọc theo tên (kw đã chuẩn hoá: chữ thường, không dấu, đã thoát ký tự LIKE, dạng %từ khoá%). */
    @Query("select m from GroupMember m where m.groupId = :groupId and m.status = :status and m.role in :roles "
            + "and m.userId in (select p.userId from UserProfile p where cast(function('unaccent', lower(concat(coalesce(p.lastName, ''), ' ', "
            + "coalesce(p.middleName, ''), ' ', coalesce(p.firstName, '')))) as string) like :kw escape '!')")
    Page<GroupMember> searchByName(@Param("groupId") UUID groupId, @Param("status") GroupMemberStatus status,
                                   @Param("roles") Collection<GroupRole> roles, @Param("kw") String keywordPattern, Pageable pageable);
}
