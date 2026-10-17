package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Các truy vấn tìm kiếm nhận mẫu từ khoá đã chuẩn hoá (chữ thường, dạng %từ khoá%, luôn khác null) để tránh lỗi tham số null
 * của PostgreSQL với lower(). Nhóm đã xóa bị loại tự động bởi @SQLRestriction trên entity.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("select g from Group g where lower(g.name) like :kw or lower(coalesce(g.description, '')) like :kw")
    Page<Group> searchAll(@Param("kw") String keywordPattern, Pageable pageable);

    /** Nhóm mà người dùng có quan hệ với trạng thái cho trước (đã tham gia, đang chờ duyệt...). */
    @Query("select g from Group g where (lower(g.name) like :kw or lower(coalesce(g.description, '')) like :kw) "
            + "and g.id in (select m.groupId from GroupMember m where m.userId = :userId and m.status = :status)")
    Page<Group> searchByMembership(@Param("kw") String keywordPattern, @Param("userId") UUID userId,
                                   @Param("status") GroupMemberStatus status, Pageable pageable);

    /** Nhóm để khám phá: loại những nhóm mình đã tham gia, đang bị cấm... (theo danh sách trạng thái loại trừ). */
    @Query("select g from Group g where (lower(g.name) like :kw or lower(coalesce(g.description, '')) like :kw) "
            + "and g.id not in (select m.groupId from GroupMember m where m.userId = :userId and m.status in :excluded)")
    Page<Group> searchDiscover(@Param("kw") String keywordPattern, @Param("userId") UUID userId,
                               @Param("excluded") Collection<GroupMemberStatus> excluded, Pageable pageable);

    boolean existsByCreatorIdAndNameIgnoreCase(UUID creatorId, String name);

    boolean existsByCreatorIdAndNameIgnoreCaseAndIdNot(UUID creatorId, String name, UUID id);
}
