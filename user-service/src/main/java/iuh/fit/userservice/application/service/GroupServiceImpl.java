package iuh.fit.userservice.application.service;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupPrivacy;
import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.domain.repository.GroupMemberRepository;
import iuh.fit.userservice.domain.repository.GroupRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import iuh.fit.userservice.infrastructure.kafka.producers.GroupEventPublisher;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.mapper.GroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final int MAX_PAGE_SIZE = 50;
    /** Sau khi bị từ chối, người dùng phải chờ khoảng này mới được xin tham gia lại (tránh spam quản trị viên). */
    private static final Duration REJECTED_COOLDOWN = Duration.ofHours(24);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final GroupMapper groupMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GroupEventPublisher groupEventPublisher;
    private final ReputationService reputationService;

    // ------------------------------------------------------------------ nhóm

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID creatorId) {
        reputationService.assertCanCreateGroup(creatorId); // điểm uy tín quá thấp thì chưa được tạo nhóm
        String name = request.getName().trim();
        if (groupRepository.existsByCreatorIdAndNameIgnoreCase(creatorId, name)) {
            throw new BusinessException(UserServiceErrorCode.GROUP_NAME_DUPLICATED);
        }
        request.setName(name);

        Group group = groupMapper.toEntity(request);
        group.setCreatorId(creatorId);
        group.setPostApprovalRequired(Boolean.TRUE.equals(request.getPostApprovalRequired()));
        group.setRules(request.getRules());
        group = groupRepository.save(group);
        saveMember(group.getId(), creatorId, GroupRole.ADMIN, GroupMemberStatus.APPROVED);

        if (request.getInitialMemberIds() != null) {
            Set<UUID> invited = new HashSet<>(request.getInitialMemberIds());
            invited.remove(creatorId);
            UUID groupId = group.getId();
            invited.forEach(id -> saveMember(groupId, id, GroupRole.MEMBER, GroupMemberStatus.APPROVED));

            String creatorName = getProfileFullName(creatorId);
            String creatorAvatar = getProfileAvatar(creatorId);
            for (UUID id : invited) {
                sendGroupNotification(id, creatorId, "GROUP_INVITE", "Lời mời tham gia nhóm",
                        creatorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, creatorAvatar);
            }
        }
        refreshMemberCount(group);
        return toResponse(group, creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID groupId, UUID viewerId) {
        return toResponse(findGroup(groupId), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroups(UUID viewerId, String scope, String keyword, int page, int size) {
        String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase()) + "%";
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase();

        Sort bySize = Sort.by(Sort.Order.desc("memberCount"), Sort.Order.desc("createdAt"));
        Sort byActivity = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("name"));
        Page<Group> groups = switch (normalizedScope) {
            case "joined" -> groupRepository.searchByMembership(pattern, viewerId, GroupMemberStatus.APPROVED,
                    PageRequest.of(pageIndex, pageSize, byActivity));
            case "pending" -> groupRepository.searchByMembership(pattern, viewerId, GroupMemberStatus.PENDING,
                    PageRequest.of(pageIndex, pageSize, byActivity));
            case "discover" -> groupRepository.searchDiscover(pattern, viewerId,
                    List.of(GroupMemberStatus.APPROVED, GroupMemberStatus.BANNED), PageRequest.of(pageIndex, pageSize, bySize));
            case "all" -> groupRepository.searchAll(pattern, PageRequest.of(pageIndex, pageSize, bySize));
            default -> throw new BusinessException(UserServiceErrorCode.GROUP_INVALID_SCOPE);
        };

        // Một truy vấn lấy quan hệ của người xem với cả trang thay vì một truy vấn cho từng nhóm
        Map<UUID, GroupMember> mine = membershipsOf(viewerId, groups.getContent());
        return groups.map(group -> toResponse(group, mine.get(group.getId())));
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId) {
        Group group = findGroup(groupId);
        requireAdmin(groupId, currentUserId);

        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            if (!name.equalsIgnoreCase(group.getName())
                    && groupRepository.existsByCreatorIdAndNameIgnoreCaseAndIdNot(group.getCreatorId(), name, groupId)) {
                throw new BusinessException(UserServiceErrorCode.GROUP_NAME_DUPLICATED);
            }
            group.setName(name);
        }
        if (request.getDescription() != null) group.setDescription(request.getDescription());
        if (request.getCoverUrl() != null) group.setCoverUrl(request.getCoverUrl().isBlank() ? null : request.getCoverUrl().trim());
        if (request.getPrivacy() != null) group.setPrivacy(request.getPrivacy());
        if (request.getPostApprovalRequired() != null) group.setPostApprovalRequired(request.getPostApprovalRequired());
        if (request.getRules() != null) group.setRules(request.getRules());
        return toResponse(groupRepository.save(group), currentUserId);
    }

    @Override
    @Transactional
    public void deleteGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroup(groupId);
        requireAdmin(groupId, currentUserId);
        group.setDeleted(true);
        groupRepository.save(group);
        groupEventPublisher.publishGroupDeleted(groupId);
    }

    // ------------------------------------------------------------- tham gia / rời

    @Override
    @Transactional
    public GroupResponse joinGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroup(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);

        if (member != null) {
            switch (member.getStatus()) {
                case BANNED -> throw new BusinessException(UserServiceErrorCode.GROUP_MEMBER_BANNED);
                case APPROVED -> {
                    return toResponse(group, member);
                }
                case PENDING -> {
                    // Bấm lần nữa khi đang chờ duyệt = hủy yêu cầu
                    groupMemberRepository.delete(member);
                    return toResponse(group, (GroupMember) null);
                }
                case REJECTED -> {
                    if (member.getReviewedAt() != null
                            && member.getReviewedAt().plus(REJECTED_COOLDOWN).isAfter(LocalDateTime.now())) {
                        throw new BusinessException(UserServiceErrorCode.GROUP_JOIN_COOLDOWN);
                    }
                }
            }
        }

        GroupMemberStatus status = group.getPrivacy() == GroupPrivacy.PUBLIC ? GroupMemberStatus.APPROVED : GroupMemberStatus.PENDING;
        if (member == null) {
            member = saveMember(groupId, currentUserId, GroupRole.MEMBER, status);
        } else {
            member.setStatus(status);
            member.setReviewedAt(null);
            member = groupMemberRepository.save(member);
        }

        if (status == GroupMemberStatus.APPROVED) {
            refreshMemberCount(group);
        } else {
            String requesterName = getProfileFullName(currentUserId);
            String requesterAvatar = getProfileAvatar(currentUserId);
            groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.APPROVED).stream()
                    .filter(m -> m.getRole() == GroupRole.ADMIN || m.getRole() == GroupRole.MODERATOR)
                    .forEach(admin -> sendGroupNotification(admin.getUserId(), currentUserId, "GROUP_JOIN_REQUEST",
                            "Yêu cầu tham gia nhóm", requesterName + " đã gửi yêu cầu tham gia nhóm \"" + group.getName() + "\".",
                            groupId, requesterAvatar));
        }
        return toResponse(group, member);
    }

    @Override
    @Transactional
    public void leaveGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroup(groupId);
        if (group.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(UserServiceErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        }
        GroupMember member = getMember(groupId, currentUserId);
        // Người bị cấm tự "rời nhóm" sẽ xóa bản ghi cấm, tức là tự gỡ cấm cho mình
        if (member.getStatus() == GroupMemberStatus.BANNED) {
            throw new BusinessException(UserServiceErrorCode.GROUP_MEMBER_BANNED);
        }
        groupMemberRepository.delete(member);
        refreshMemberCount(group);
    }

    // ------------------------------------------------------------------ thành viên

    @Override
    @Transactional
    public GroupResponse reviewMember(UUID groupId, UUID memberId, boolean approved, UUID currentUserId) {
        Group group = findGroup(groupId);
        requireModerator(groupId, currentUserId);
        GroupMember member = getMember(groupId, memberId);

        if (member.getStatus() == GroupMemberStatus.PENDING) {
            member.setReviewedAt(LocalDateTime.now());
            if (approved) {
                member.setStatus(GroupMemberStatus.APPROVED);
                groupMemberRepository.save(member);
                refreshMemberCount(group);
                sendGroupNotification(memberId, currentUserId, "GROUP_JOIN_ACCEPT", "Yêu cầu tham gia nhóm",
                        getProfileFullName(currentUserId) + " đã phê duyệt yêu cầu tham gia nhóm \"" + group.getName() + "\" của bạn.",
                        groupId, getProfileAvatar(currentUserId));
            } else {
                member.setStatus(GroupMemberStatus.REJECTED);
                groupMemberRepository.save(member);
            }
        }
        return toResponse(group, currentUserId);
    }

    @Override
    @Transactional
    public GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId) {
        Group group = findGroup(groupId);
        requireModerator(groupId, currentUserId);

        Set<UUID> newlyAdded = new HashSet<>();
        for (UUID id : new HashSet<>(memberIds)) {
            GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, id).orElse(null);
            if (member == null) {
                saveMember(groupId, id, GroupRole.MEMBER, GroupMemberStatus.APPROVED);
                newlyAdded.add(id);
            } else if (member.getStatus() != GroupMemberStatus.APPROVED && member.getStatus() != GroupMemberStatus.BANNED) {
                member.setStatus(GroupMemberStatus.APPROVED);
                groupMemberRepository.save(member);
                newlyAdded.add(id);
            }
        }
        if (!newlyAdded.isEmpty()) refreshMemberCount(group);

        String actorName = getProfileFullName(currentUserId);
        String actorAvatar = getProfileAvatar(currentUserId);
        for (UUID id : newlyAdded) {
            sendGroupNotification(id, currentUserId, "GROUP_INVITE", "Lời mời tham gia nhóm",
                    actorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, actorAvatar);
        }
        return toResponse(group, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(UUID groupId, UUID currentUserId, boolean includePending) {
        Group group = findGroup(groupId);
        GroupMember me = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);
        boolean approved = me != null && me.getStatus() == GroupMemberStatus.APPROVED;
        if (group.getPrivacy() == GroupPrivacy.PRIVATE && !approved) {
            throw new BusinessException(UserServiceErrorCode.GROUP_MEMBERS_PRIVATE);
        }
        boolean canReview = approved && (me.getRole() == GroupRole.ADMIN || me.getRole() == GroupRole.MODERATOR);

        List<GroupMember> visible = groupMemberRepository.findAllByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == GroupMemberStatus.APPROVED
                        || (includePending && canReview
                        && (m.getStatus() == GroupMemberStatus.PENDING || m.getStatus() == GroupMemberStatus.BANNED)))
                .toList();

        // Một truy vấn lấy hồ sơ của tất cả thành viên thay vì một truy vấn cho từng người
        Map<UUID, UserProfile> profiles = userProfileRepository
                .findByUserIdIn(visible.stream().map(GroupMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p, (a, b) -> a));
        return visible.stream().map(m -> toMemberResponse(m, profiles.get(m.getUserId()))).toList();
    }

    @Override
    @Transactional
    public void changeRole(UUID groupId, UUID memberId, GroupRole role, UUID currentUserId) {
        Group group = findGroup(groupId);
        requireAdmin(groupId, currentUserId);
        GroupMember member = getMember(groupId, memberId);
        if (member.getUserId().equals(group.getCreatorId())) {
            throw new BusinessException(UserServiceErrorCode.GROUP_OWNER_PROTECTED);
        }
        member.setRole(role);
        groupMemberRepository.save(member);

        String roleLabel = role == GroupRole.ADMIN ? "Quản trị viên" : role == GroupRole.MODERATOR ? "Người kiểm duyệt" : "Thành viên";
        sendGroupNotification(memberId, currentUserId, "GROUP_ROLE_CHANGE", "Cập nhật vai trò nhóm",
                getProfileFullName(currentUserId) + " đã thay đổi vai trò của bạn trong nhóm \"" + group.getName() + "\" thành " + roleLabel + ".",
                groupId, getProfileAvatar(currentUserId));
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID memberId, boolean ban, UUID currentUserId) {
        Group group = findGroup(groupId);
        GroupMember actor = requireModerator(groupId, currentUserId);
        GroupMember member = getMember(groupId, memberId);
        if (member.getUserId().equals(group.getCreatorId())) {
            throw new BusinessException(UserServiceErrorCode.GROUP_OWNER_PROTECTED);
        }
        // Người kiểm duyệt không được xóa/cấm quản trị viên (bảo vệ phân cấp vai trò)
        if (member.getRole() == GroupRole.ADMIN && actor.getRole() != GroupRole.ADMIN) {
            throw new BusinessException(UserServiceErrorCode.GROUP_ADMIN_PROTECTED);
        }
        if (ban) {
            member.setStatus(GroupMemberStatus.BANNED);
            groupMemberRepository.save(member);
            sendGroupNotification(memberId, currentUserId, "SYSTEM", "Thông báo từ nhóm",
                    "Bạn đã bị cấm khỏi nhóm \"" + group.getName() + "\".", groupId, getProfileAvatar(currentUserId));
        } else {
            groupMemberRepository.delete(member);
        }
        refreshMemberCount(group);
    }

    @Override
    @Transactional
    public void unbanMember(UUID groupId, UUID memberId, UUID currentUserId) {
        findGroup(groupId);
        requireModerator(groupId, currentUserId);
        GroupMember member = getMember(groupId, memberId);
        if (member.getStatus() != GroupMemberStatus.BANNED) {
            throw new BusinessException(UserServiceErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
        groupMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public void transferOwnership(UUID groupId, UUID newOwnerId, UUID currentUserId) {
        Group group = findGroup(groupId);
        if (!group.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
        }
        if (newOwnerId.equals(currentUserId)) {
            return;
        }
        GroupMember target = getMember(groupId, newOwnerId);
        if (target.getStatus() != GroupMemberStatus.APPROVED) {
            throw new BusinessException(UserServiceErrorCode.GROUP_TRANSFER_TARGET_INVALID);
        }
        target.setRole(GroupRole.ADMIN);
        groupMemberRepository.save(target);
        group.setCreatorId(newOwnerId);
        groupRepository.save(group);

        sendGroupNotification(newOwnerId, currentUserId, "GROUP_ROLE_CHANGE", "Bạn là chủ nhóm mới",
                getProfileFullName(currentUserId) + " đã chuyển quyền chủ nhóm \"" + group.getName() + "\" cho bạn.",
                groupId, getProfileAvatar(currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFeedVisibilityResponse> getFeedVisibility(List<UUID> groupIds, UUID viewerId) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        Set<UUID> requested = new LinkedHashSet<>(groupIds);
        // Nhóm đã xóa không có trong kết quả này (@SQLRestriction) nên không thể tính là "đang là thành viên"
        Map<UUID, Group> groups = groupRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(Group::getId, group -> group));
        Map<UUID, GroupMember> memberships = groupMemberRepository.findAllByUserIdAndStatus(viewerId, GroupMemberStatus.APPROVED)
                .stream().collect(Collectors.toMap(GroupMember::getGroupId, member -> member, (left, right) -> left));
        return requested.stream().map(groupId -> {
            Group group = groups.get(groupId);
            GroupMember membership = group == null ? null : memberships.get(groupId);
            return GroupFeedVisibilityResponse.builder()
                    .groupId(groupId)
                    .member(membership != null)
                    .publicGroup(group != null && group.getPrivacy() == GroupPrivacy.PUBLIC)
                    .role(membership != null ? membership.getRole() : null)
                    .postApprovalRequired(group != null && group.isPostApprovalRequired())
                    .build();
        }).toList();
    }

    // ------------------------------------------------------------------ hỗ trợ

    private Group findGroup(UUID id) {
        return groupRepository.findById(id).orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_FOUND));
    }

    private GroupMember getMember(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_MEMBER_NOT_FOUND));
    }

    private GroupMember saveMember(UUID groupId, UUID userId, GroupRole role, GroupMemberStatus status) {
        return groupMemberRepository.save(GroupMember.builder().groupId(groupId).userId(userId).role(role).status(status).build());
    }

    /** Đếm lại số thành viên đã duyệt từ DB: không lệch khi nhiều người vào/ra cùng lúc như cộng/trừ tay. */
    private void refreshMemberCount(Group group) {
        group.setMemberCount(groupMemberRepository.countByGroupIdAndStatus(group.getId(), GroupMemberStatus.APPROVED));
        groupRepository.save(group);
    }

    private GroupMember requireModerator(UUID groupId, UUID userId) {
        GroupMember m = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.FORBIDDEN));
        if (m.getStatus() != GroupMemberStatus.APPROVED || (m.getRole() != GroupRole.ADMIN && m.getRole() != GroupRole.MODERATOR)) {
            throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
        }
        return m;
    }

    private void requireAdmin(UUID groupId, UUID userId) {
        if (requireModerator(groupId, userId).getRole() != GroupRole.ADMIN) {
            throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
        }
    }

    private Map<UUID, GroupMember> membershipsOf(UUID viewerId, Collection<Group> groups) {
        if (viewerId == null || groups.isEmpty()) return Map.of();
        return groupMemberRepository.findAllByUserIdAndGroupIdIn(viewerId, groups.stream().map(Group::getId).toList()).stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, m -> m, (a, b) -> a));
    }

    private GroupResponse toResponse(Group group, UUID viewerId) {
        GroupMember me = viewerId == null ? null : groupMemberRepository.findByGroupIdAndUserId(group.getId(), viewerId).orElse(null);
        return toResponse(group, me);
    }

    private GroupResponse toResponse(Group group, GroupMember me) {
        GroupResponse response = groupMapper.toResponse(group);
        boolean approved = me != null && me.getStatus() == GroupMemberStatus.APPROVED;
        response.setOwnerId(group.getCreatorId());
        response.setMember(approved);
        response.setAdmin(approved && me.getRole() == GroupRole.ADMIN);
        response.setModerator(approved && me.getRole() == GroupRole.MODERATOR);
        response.setJoinStatus(me == null ? null : me.getStatus());
        return response;
    }

    private GroupMemberResponse toMemberResponse(GroupMember m, UserProfile p) {
        String name = p == null ? "Thành viên" : Arrays.asList(p.getLastName(), p.getMiddleName(), p.getFirstName()).stream()
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).collect(Collectors.joining(" "));
        return GroupMemberResponse.builder()
                .userId(m.getUserId())
                .name(name.isBlank() ? "Thành viên" : name)
                .avatarUrl(p == null ? null : p.getAvatarUrl())
                .role(m.getRole())
                .status(m.getStatus())
                .joinedAt(m.getJoinedAt())
                .build();
    }

    private void sendGroupNotification(UUID recipientId, UUID actorId, String type, String title, String content, UUID groupId, String avatarUrl) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        try {
            Map<String, Object> notifEvent = new HashMap<>();
            notifEvent.put("recipientId", recipientId.toString());
            notifEvent.put("actorId", actorId != null ? actorId.toString() : null);
            notifEvent.put("type", type);
            notifEvent.put("title", title);
            notifEvent.put("content", content);
            notifEvent.put("targetId", groupId != null ? groupId.toString() : null);
            notifEvent.put("targetUrl", groupId != null ? "/groups/" + groupId : "/groups");
            notifEvent.put("avatarUrl", avatarUrl);

            kafkaTemplate.send("notification.in-app.send", notifEvent);
            log.info("Published group notification event {} to Kafka for recipient: {}", type, recipientId);
        } catch (Exception e) {
            log.warn("Failed to publish group notification event: {}", e.getMessage());
        }
    }

    private String getProfileFullName(UUID userId) {
        if (userId == null) return "Một người dùng";
        UserProfile p = userProfileRepository.findByUserId(userId).orElse(null);
        if (p == null) return "Một người dùng";
        String name = Arrays.asList(p.getLastName(), p.getMiddleName(), p.getFirstName()).stream()
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).collect(Collectors.joining(" ")).trim();
        return name.isBlank() ? "Một người dùng" : name;
    }

    private String getProfileAvatar(UUID userId) {
        if (userId == null) return null;
        UserProfile p = userProfileRepository.findByUserId(userId).orElse(null);
        return p != null ? p.getAvatarUrl() : null;
    }
}
