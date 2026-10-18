package iuh.fit.userservice.application.service;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupPrivacy;
import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.domain.repository.GroupMemberRepository;
import iuh.fit.userservice.domain.repository.GroupRepository;
import iuh.fit.userservice.domain.repository.UserBlockRepository;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import iuh.fit.userservice.infrastructure.kafka.producers.GroupEventPublisher;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.dto.response.GroupStaffResponse;
import iuh.fit.userservice.presentation.mapper.GroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_QUESTIONS = 3;
    private static final int MAX_QUESTION_LENGTH = 200;
    private static final int MAX_ANSWER_LENGTH = 300;
    private static final int MEMBER_PREVIEW_SIZE = 8;
    /** Giới hạn số người nhận thông báo "mọi bài viết mới" cho một bài (tránh bão thông báo ở nhóm rất lớn). */
    private static final int MAX_SUBSCRIBERS_PER_POST = 500;
    private static final List<GroupRole> STAFF_ROLES = List.of(GroupRole.ADMIN, GroupRole.MODERATOR);
    private static final List<GroupRole> ALL_ROLES = List.of(GroupRole.ADMIN, GroupRole.MODERATOR, GroupRole.MEMBER);
    /** Sau khi bị từ chối, người dùng phải chờ khoảng này mới được xin tham gia lại (tránh spam quản trị viên). */
    private static final Duration REJECTED_COOLDOWN = Duration.ofHours(24);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConnectionRepository userConnectionRepository;
    private final UserBlockRepository userBlockRepository;
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
        group.setMembershipQuestions(joinQuestions(request.getMembershipQuestions()));
        group = groupRepository.save(group);
        saveMember(group.getId(), creatorId, GroupRole.ADMIN, GroupMemberStatus.APPROVED);

        if (request.getInitialMemberIds() != null) {
            Set<UUID> invited = new HashSet<>(request.getInitialMemberIds());
            invited.remove(creatorId);
            requireFriends(creatorId, invited);
            UUID groupId = group.getId();
            invited.forEach(id -> {
                GroupMember added = saveMember(groupId, id, GroupRole.MEMBER, GroupMemberStatus.APPROVED);
                added.setInvitedBy(creatorId);
                groupMemberRepository.save(added);
            });

            String creatorName = getProfileFullName(creatorId);
            String creatorAvatar = getProfileAvatar(creatorId);
            for (UUID id : invited) {
                sendGroupNotification(id, creatorId, "GROUP_INVITE", "Lời mời tham gia nhóm",
                        creatorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, creatorAvatar);
            }
        }
        refreshMemberCount(group);
        return viewFor(group, creatorId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID groupId, UUID viewerId) {
        return viewFor(findGroup(groupId), viewerId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroups(UUID viewerId, String scope, String keyword, int page, int size) {
        String pattern = likePattern(keyword);
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase(Locale.ROOT);

        Sort bySize = Sort.by(Sort.Order.desc("memberCount"), Sort.Order.desc("createdAt"));
        Sort byActivity = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("name"));
        Page<Group> groups = switch (normalizedScope) {
            case "joined" -> groupRepository.searchByMembership(pattern, viewerId, GroupMemberStatus.APPROVED,
                    PageRequest.of(pageIndex, pageSize, byActivity));
            case "pending" -> groupRepository.searchByMembership(pattern, viewerId, GroupMemberStatus.PENDING,
                    PageRequest.of(pageIndex, pageSize, byActivity));
            case "invited" -> groupRepository.searchByMembership(pattern, viewerId, GroupMemberStatus.INVITED,
                    PageRequest.of(pageIndex, pageSize, byActivity));
            case "discover" -> groupRepository.searchDiscover(pattern, viewerId,
                    List.of(GroupMemberStatus.APPROVED, GroupMemberStatus.BANNED, GroupMemberStatus.INVITED),
                    PageRequest.of(pageIndex, pageSize, bySize));
            case "all" -> groupRepository.searchAll(pattern, PageRequest.of(pageIndex, pageSize, bySize));
            default -> throw new BusinessException(UserServiceErrorCode.GROUP_INVALID_SCOPE);
        };

        // Một truy vấn lấy quan hệ của người xem với cả trang thay vì một truy vấn cho từng nhóm
        Map<UUID, GroupMember> mine = membershipsOf(viewerId, groups.getContent());
        return groups.map(group -> view(group, mine.get(group.getId())));
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
        if (request.getMembershipQuestions() != null) group.setMembershipQuestions(joinQuestions(request.getMembershipQuestions()));
        return viewFor(groupRepository.save(group), currentUserId, true);
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
    public GroupResponse joinGroup(UUID groupId, List<String> answers, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);

        if (member != null) {
            switch (member.getStatus()) {
                case BANNED -> throw new BusinessException(UserServiceErrorCode.GROUP_MEMBER_BANNED);
                // Bấm tham gia nhiều lần (hoặc hai tab cùng lúc) chỉ có tác dụng một lần: giữ nguyên trạng thái hiện tại
                case APPROVED, PENDING -> {
                    return viewFor(group, currentUserId, true);
                }
                case REJECTED -> {
                    if (member.getReviewedAt() != null
                            && member.getReviewedAt().plus(REJECTED_COOLDOWN).isAfter(LocalDateTime.now())) {
                        throw new BusinessException(UserServiceErrorCode.GROUP_JOIN_COOLDOWN);
                    }
                }
                case INVITED -> { /* đồng ý lời mời: đi tiếp như một yêu cầu tham gia */ }
            }
        }

        boolean invitedByStaff = member != null && member.getStatus() == GroupMemberStatus.INVITED
                && isStaff(groupId, member.getInvitedBy());
        GroupMemberStatus status = group.getPrivacy() == GroupPrivacy.PUBLIC || invitedByStaff
                ? GroupMemberStatus.APPROVED : GroupMemberStatus.PENDING;
        String storedAnswers = null;
        if (status == GroupMemberStatus.PENDING) {
            storedAnswers = validateAnswers(group, answers);
        }

        if (member == null) {
            member = saveMember(groupId, currentUserId, GroupRole.MEMBER, status);
        } else {
            member.setStatus(status);
            member.setReviewedAt(null);
        }
        member.setJoinAnswers(storedAnswers);
        member = groupMemberRepository.save(member);

        if (status == GroupMemberStatus.APPROVED) {
            refreshMemberCount(group);
        } else {
            notifyStaffOfJoinRequest(group, currentUserId);
        }
        return viewFor(group, currentUserId, true);
    }

    @Override
    @Transactional
    public GroupResponse cancelJoinRequest(UUID groupId, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
        groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .filter(m -> m.getStatus() == GroupMemberStatus.PENDING)
                .ifPresent(groupMemberRepository::delete);
        return viewFor(group, currentUserId, true);
    }

    @Override
    @Transactional
    public void leaveGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
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
        Group group = findGroupForUpdate(groupId);
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
        return viewFor(group, currentUserId, true);
    }

    @Override
    @Transactional
    public GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
        GroupMember actor = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .filter(m -> m.getStatus() == GroupMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_MEMBER));
        boolean staff = actor.getRole() == GroupRole.ADMIN || actor.getRole() == GroupRole.MODERATOR;

        Set<UUID> targets = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        targets.remove(currentUserId);
        requireFriends(currentUserId, targets);

        Set<UUID> added = new LinkedHashSet<>();
        Set<UUID> invited = new LinkedHashSet<>();
        for (UUID id : targets) {
            if (userBlockRepository.existsByBlockerIdAndBlockedId(id, currentUserId)
                    || userBlockRepository.existsByBlockerIdAndBlockedId(currentUserId, id)) {
                continue;
            }
            GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, id).orElse(null);
            if (member == null) {
                member = saveMember(groupId, id, GroupRole.MEMBER, staff ? GroupMemberStatus.APPROVED : GroupMemberStatus.INVITED);
                member.setInvitedBy(currentUserId);
                groupMemberRepository.save(member);
                (staff ? added : invited).add(id);
                continue;
            }
            switch (member.getStatus()) {
                case APPROVED, BANNED -> { /* đã ở trong nhóm hoặc bị cấm: bỏ qua */ }
                case INVITED -> {
                    if (staff) { // quản trị viên thêm thẳng người đang được mời
                        member.setStatus(GroupMemberStatus.APPROVED);
                        member.setInvitedBy(currentUserId);
                        groupMemberRepository.save(member);
                        added.add(id);
                    }
                }
                case PENDING -> {
                    if (staff) { // người này đã xin vào: thêm thẳng cũng là chấp thuận
                        member.setStatus(GroupMemberStatus.APPROVED);
                        member.setReviewedAt(LocalDateTime.now());
                        groupMemberRepository.save(member);
                        added.add(id);
                    }
                }
                case REJECTED -> {
                    member.setStatus(staff ? GroupMemberStatus.APPROVED : GroupMemberStatus.INVITED);
                    member.setInvitedBy(currentUserId);
                    member.setReviewedAt(null);
                    groupMemberRepository.save(member);
                    (staff ? added : invited).add(id);
                }
            }
        }
        if (!added.isEmpty()) refreshMemberCount(group);

        String actorName = getProfileFullName(currentUserId);
        String actorAvatar = getProfileAvatar(currentUserId);
        for (UUID id : added) {
            sendGroupNotification(id, currentUserId, "GROUP_INVITE", "Lời mời tham gia nhóm",
                    actorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, actorAvatar);
        }
        for (UUID id : invited) {
            sendGroupNotification(id, currentUserId, "GROUP_INVITE", "Lời mời tham gia nhóm",
                    actorName + " đã mời bạn tham gia nhóm \"" + group.getName() + "\".", groupId, actorAvatar);
        }
        return viewFor(group, currentUserId, true);
    }

    @Override
    @Transactional
    public GroupResponse respondToInvitation(UUID groupId, boolean accept, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .filter(m -> m.getStatus() == GroupMemberStatus.INVITED)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_INVITATION_NOT_FOUND));
        if (!accept) {
            groupMemberRepository.delete(member);
            return viewFor(group, currentUserId, true);
        }
        // Lời mời của quản trị/kiểm duyệt viên (hoặc nhóm công khai) được vào thẳng; lời mời của thành viên thường
        // ở nhóm riêng tư vẫn cần quản trị viên duyệt
        boolean direct = group.getPrivacy() == GroupPrivacy.PUBLIC || isStaff(groupId, member.getInvitedBy());
        if (direct) {
            member.setStatus(GroupMemberStatus.APPROVED);
            groupMemberRepository.save(member);
            refreshMemberCount(group);
            sendGroupNotification(member.getInvitedBy(), currentUserId, "GROUP_JOIN_ACCEPT", "Lời mời đã được chấp nhận",
                    getProfileFullName(currentUserId) + " đã tham gia nhóm \"" + group.getName() + "\" theo lời mời của bạn.",
                    groupId, getProfileAvatar(currentUserId));
        } else {
            member.setStatus(GroupMemberStatus.PENDING);
            groupMemberRepository.save(member);
            notifyStaffOfJoinRequest(group, currentUserId);
        }
        return viewFor(group, currentUserId, true);
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
                        || (includePending && canReview && m.getStatus() != GroupMemberStatus.REJECTED))
                .toList();
        return toMemberResponses(visible, canReview, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupMemberResponse> getMembersPage(UUID groupId, UUID currentUserId, String scope, String keyword, int page, int size) {
        Group group = findGroup(groupId);
        GroupMember me = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);
        boolean approved = me != null && me.getStatus() == GroupMemberStatus.APPROVED;
        if (group.getPrivacy() == GroupPrivacy.PRIVATE && !approved) {
            throw new BusinessException(UserServiceErrorCode.GROUP_MEMBERS_PRIVATE);
        }
        boolean canReview = approved && (me.getRole() == GroupRole.ADMIN || me.getRole() == GroupRole.MODERATOR);

        String normalizedScope = scope == null || scope.isBlank() ? "all" : scope.trim().toLowerCase(Locale.ROOT);
        GroupMemberStatus status;
        Collection<GroupRole> roles = ALL_ROLES;
        Sort sort = Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.asc("id"));
        switch (normalizedScope) {
            case "all" -> status = GroupMemberStatus.APPROVED;
            case "members" -> { status = GroupMemberStatus.APPROVED; roles = List.of(GroupRole.MEMBER); }
            case "staff" -> { status = GroupMemberStatus.APPROVED; roles = STAFF_ROLES; }
            case "pending", "banned", "invited" -> {
                if (!canReview) throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
                status = switch (normalizedScope) {
                    case "pending" -> GroupMemberStatus.PENDING;
                    case "banned" -> GroupMemberStatus.BANNED;
                    default -> GroupMemberStatus.INVITED;
                };
                // Yêu cầu chờ duyệt: người gửi sớm nhất được xem xét trước
                if (status == GroupMemberStatus.PENDING) sort = Sort.by(Sort.Order.asc("joinedAt"), Sort.Order.asc("id"));
            }
            default -> throw new BusinessException(UserServiceErrorCode.GROUP_INVALID_MEMBER_SCOPE);
        }

        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        boolean searching = keyword != null && !keyword.isBlank();
        Page<GroupMember> result = searching
                ? groupMemberRepository.searchByName(groupId, status, roles, likePattern(keyword), pageable)
                : groupMemberRepository.findByGroupIdAndStatusAndRoleIn(groupId, status, roles, pageable);

        boolean showAnswers = canReview && status == GroupMemberStatus.PENDING;
        List<GroupMemberResponse> content = toMemberResponses(result.getContent(), showAnswers, status == GroupMemberStatus.INVITED);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupStaffResponse> getStaff(UUID groupId) {
        Group group = findGroup(groupId);
        List<GroupMember> staff = groupMemberRepository.findAllByGroupIdAndStatusAndRoleIn(groupId, GroupMemberStatus.APPROVED, STAFF_ROLES);
        Map<UUID, UserProfile> profiles = profilesOf(staff.stream().map(GroupMember::getUserId).toList());
        return staff.stream()
                .sorted(Comparator
                        .comparing((GroupMember m) -> !m.getUserId().equals(group.getCreatorId()))
                        .thenComparing(m -> m.getRole() != GroupRole.ADMIN)
                        .thenComparing(m -> m.getJoinedAt() == null ? LocalDateTime.MAX : m.getJoinedAt()))
                .map(m -> GroupStaffResponse.builder()
                        .userId(m.getUserId())
                        .name(fullName(profiles.get(m.getUserId())))
                        .avatarUrl(avatarOf(profiles.get(m.getUserId())))
                        .role(m.getRole())
                        .owner(m.getUserId().equals(group.getCreatorId()))
                        .build())
                .toList();
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
        if (member.getStatus() != GroupMemberStatus.APPROVED) {
            throw new BusinessException(UserServiceErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
        if (member.getRole() == role) return;
        member.setRole(role);
        groupMemberRepository.save(member);

        String roleLabel = role == GroupRole.ADMIN ? "Quản trị viên" : role == GroupRole.MODERATOR ? "Người kiểm duyệt" : "Thành viên";
        sendGroupNotification(memberId, currentUserId, "GROUP_ROLE_CHANGE", "Cập nhật vai trò nhóm",
                getProfileFullName(currentUserId) + " đã thay đổi vai trò của bạn trong nhóm \"" + group.getName() + "\" thành " + roleLabel + ".",
                groupId, getProfileAvatar(currentUserId));
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID memberId, boolean ban, boolean deleteContent, UUID currentUserId) {
        Group group = findGroupForUpdate(groupId);
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
            member.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(member);
            sendGroupNotification(memberId, currentUserId, "SYSTEM", "Thông báo từ nhóm",
                    "Bạn đã bị cấm khỏi nhóm \"" + group.getName() + "\".", groupId, getProfileAvatar(currentUserId));
        } else {
            groupMemberRepository.delete(member);
        }
        refreshMemberCount(group);
        if (deleteContent) groupEventPublisher.publishMemberContentRemoved(groupId, memberId);
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
        Group group = findGroupForUpdate(groupId);
        if (!group.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
        }
        if (newOwnerId.equals(currentUserId)) {
            throw new BusinessException(UserServiceErrorCode.GROUP_TRANSFER_TARGET_INVALID);
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
    @Transactional
    public void setNotificationLevel(UUID groupId, GroupNotificationLevel level, UUID currentUserId) {
        findGroup(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .filter(m -> m.getStatus() == GroupMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_MEMBER));
        member.setNotificationLevel(level);
        groupMemberRepository.save(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getJoinedGroupIds(UUID userId) {
        return groupMemberRepository.findAllByUserIdAndStatus(userId, GroupMemberStatus.APPROVED).stream()
                .map(GroupMember::getGroupId)
                .toList();
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

    @Override
    @Transactional(readOnly = true)
    public NotifyContext getNotifyContext(UUID groupId, UUID actorId) {
        Group group = findGroup(groupId);
        List<UUID> staffIds = groupMemberRepository
                .findAllByGroupIdAndStatusAndRoleIn(groupId, GroupMemberStatus.APPROVED, STAFF_ROLES).stream()
                .map(GroupMember::getUserId).toList();
        List<UUID> subscriberIds = groupMemberRepository
                .findAllByGroupIdAndStatusAndNotificationLevel(groupId, GroupMemberStatus.APPROVED, GroupNotificationLevel.ALL).stream()
                .map(GroupMember::getUserId).limit(MAX_SUBSCRIBERS_PER_POST).toList();
        return new NotifyContext(groupId, group.getName(), staffIds, subscriberIds,
                actorId == null ? null : getProfileFullName(actorId), actorId == null ? null : getProfileAvatar(actorId));
    }

    // ------------------------------------------------------------------ hỗ trợ

    private Group findGroup(UUID id) {
        return groupRepository.findById(id).orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_FOUND));
    }

    private Group findGroupForUpdate(UUID id) {
        return groupRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_FOUND));
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

    /** Người này hiện có phải quản trị/kiểm duyệt viên (đã được duyệt) của nhóm không. */
    private boolean isStaff(UUID groupId, UUID userId) {
        if (userId == null) return false;
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(m -> m.getStatus() == GroupMemberStatus.APPROVED)
                .map(m -> m.getRole() == GroupRole.ADMIN || m.getRole() == GroupRole.MODERATOR)
                .orElse(false);
    }

    /** Chỉ được thêm/mời bạn bè của mình: chặn việc gửi lời mời hàng loạt cho người lạ. */
    private void requireFriends(UUID userId, Collection<UUID> targets) {
        for (UUID target : targets) {
            if (userConnectionRepository.findAllAcceptedFriendshipsBetween(userId, target).isEmpty()) {
                throw new BusinessException(UserServiceErrorCode.GROUP_INVITE_NOT_FRIEND);
            }
        }
    }

    private void notifyStaffOfJoinRequest(Group group, UUID requesterId) {
        String requesterName = getProfileFullName(requesterId);
        String requesterAvatar = getProfileAvatar(requesterId);
        groupMemberRepository.findAllByGroupIdAndStatusAndRoleIn(group.getId(), GroupMemberStatus.APPROVED, STAFF_ROLES)
                .forEach(admin -> sendGroupNotification(admin.getUserId(), requesterId, "GROUP_JOIN_REQUEST",
                        "Yêu cầu tham gia nhóm", requesterName + " đã gửi yêu cầu tham gia nhóm \"" + group.getName() + "\".",
                        group.getId(), requesterAvatar));
    }

    private Map<UUID, GroupMember> membershipsOf(UUID viewerId, Collection<Group> groups) {
        if (viewerId == null || groups.isEmpty()) return Map.of();
        return groupMemberRepository.findAllByUserIdAndGroupIdIn(viewerId, groups.stream().map(Group::getId).toList()).stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, m -> m, (a, b) -> a));
    }

    // ----- câu hỏi tham gia

    private static String joinQuestions(List<String> questions) {
        if (questions == null) return null;
        List<String> cleaned = questions.stream()
                .filter(Objects::nonNull)
                .map(q -> singleLine(q, MAX_QUESTION_LENGTH))
                .filter(q -> !q.isEmpty())
                .limit(MAX_QUESTIONS)
                .toList();
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private static List<String> questionsOf(Group group) {
        String raw = group.getMembershipQuestions();
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\n")).map(String::trim).filter(q -> !q.isEmpty()).toList();
    }

    private static String singleLine(String text, int maxLength) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    /** Nhóm riêng tư có câu hỏi thì người xin vào phải trả lời đủ; trả về chuỗi câu trả lời để lưu (hoặc null nếu không có câu hỏi). */
    private String validateAnswers(Group group, List<String> answers) {
        List<String> questions = group.getPrivacy() == GroupPrivacy.PRIVATE ? questionsOf(group) : List.of();
        if (questions.isEmpty()) return null;
        List<String> cleaned = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String answer = answers != null && i < answers.size() && answers.get(i) != null
                    ? singleLine(answers.get(i), MAX_ANSWER_LENGTH) : "";
            if (answer.isEmpty()) throw new BusinessException(UserServiceErrorCode.GROUP_ANSWERS_REQUIRED);
            cleaned.add(answer);
        }
        return String.join("\n", cleaned);
    }

    // ----- tìm kiếm

    /** Từ khoá → mẫu LIKE: bỏ dấu, chữ thường, thoát % _ ! (! là ký tự thoát của LIKE) để người dùng gõ ký tự đặc biệt không thành ký tự đại diện. */
    static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > 100) k = k.substring(0, 100);
        k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        k = k.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }

    // ----- chuyển đổi sang response

    private GroupResponse viewFor(Group group, UUID viewerId, boolean detailed) {
        GroupMember me = viewerId == null ? null : groupMemberRepository.findByGroupIdAndUserId(group.getId(), viewerId).orElse(null);
        GroupResponse response = view(group, me);
        if (detailed) addDetails(response, group, me);
        return response;
    }

    private GroupResponse view(Group group, GroupMember me) {
        GroupResponse response = groupMapper.toResponse(group);
        boolean approved = me != null && me.getStatus() == GroupMemberStatus.APPROVED;
        response.setOwnerId(group.getCreatorId());
        response.setMember(approved);
        response.setAdmin(approved && me.getRole() == GroupRole.ADMIN);
        response.setModerator(approved && me.getRole() == GroupRole.MODERATOR);
        response.setJoinStatus(me == null ? null : me.getStatus());
        response.setMembershipQuestions(questionsOf(group));
        if (approved) {
            response.setNotificationLevel(me.getNotificationLevel() == null ? GroupNotificationLevel.HIGHLIGHTS : me.getNotificationLevel());
        }
        return response;
    }

    /** Thêm các thông tin tốn thêm truy vấn: chỉ dành cho trang chi tiết của một nhóm, không dùng khi liệt kê nhiều nhóm. */
    private void addDetails(GroupResponse response, Group group, GroupMember me) {
        boolean approved = me != null && me.getStatus() == GroupMemberStatus.APPROVED;
        boolean staff = approved && (me.getRole() == GroupRole.ADMIN || me.getRole() == GroupRole.MODERATOR);
        if (staff) {
            response.setPendingRequestCount(groupMemberRepository.countByGroupIdAndStatus(group.getId(), GroupMemberStatus.PENDING));
        }
        response.setNewMembersThisWeek(groupMemberRepository.countByGroupIdAndStatusAndJoinedAtAfter(
                group.getId(), GroupMemberStatus.APPROVED, LocalDateTime.now().minusDays(7)));
        if (me != null && me.getStatus() == GroupMemberStatus.INVITED && me.getInvitedBy() != null) {
            response.setInvitedByName(getProfileFullName(me.getInvitedBy()));
        }
        if (group.getPrivacy() == GroupPrivacy.PUBLIC || approved) {
            Pageable newest = PageRequest.of(0, MEMBER_PREVIEW_SIZE, Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.asc("id")));
            List<GroupMember> preview = groupMemberRepository
                    .findByGroupIdAndStatusAndRoleIn(group.getId(), GroupMemberStatus.APPROVED, ALL_ROLES, newest).getContent();
            Map<UUID, UserProfile> profiles = profilesOf(preview.stream().map(GroupMember::getUserId).toList());
            response.setMemberPreview(preview.stream()
                    .map(m -> GroupResponse.MemberPreview.builder()
                            .userId(m.getUserId())
                            .name(fullName(profiles.get(m.getUserId())))
                            .avatarUrl(avatarOf(profiles.get(m.getUserId())))
                            .build())
                    .toList());
        }
    }

    private List<GroupMemberResponse> toMemberResponses(List<GroupMember> members, boolean includeAnswers, boolean includeInviter) {
        Set<UUID> profileIds = new HashSet<>(members.stream().map(GroupMember::getUserId).toList());
        if (includeInviter) members.stream().map(GroupMember::getInvitedBy).filter(Objects::nonNull).forEach(profileIds::add);
        // Một truy vấn lấy hồ sơ của tất cả thành viên thay vì một truy vấn cho từng người
        Map<UUID, UserProfile> profiles = profilesOf(new ArrayList<>(profileIds));
        return members.stream().map(m -> GroupMemberResponse.builder()
                .userId(m.getUserId())
                .name(fullName(profiles.get(m.getUserId())))
                .avatarUrl(avatarOf(profiles.get(m.getUserId())))
                .role(m.getRole())
                .status(m.getStatus())
                .joinedAt(m.getJoinedAt())
                .answers(includeAnswers && m.getJoinAnswers() != null ? Arrays.asList(m.getJoinAnswers().split("\n")) : null)
                .invitedByName(includeInviter && m.getInvitedBy() != null ? fullName(profiles.get(m.getInvitedBy())) : null)
                .build()).toList();
    }

    private Map<UUID, UserProfile> profilesOf(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p, (a, b) -> a));
    }

    private static String fullName(UserProfile p) {
        if (p == null) return "Thành viên";
        String name = Arrays.asList(p.getLastName(), p.getMiddleName(), p.getFirstName()).stream()
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).collect(Collectors.joining(" "));
        return name.isBlank() ? "Thành viên" : name;
    }

    private static String avatarOf(UserProfile p) {
        return p == null ? null : p.getAvatarUrl();
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
        String name = fullName(p);
        return "Thành viên".equals(name) ? "Một người dùng" : name;
    }

    private String getProfileAvatar(UUID userId) {
        if (userId == null) return null;
        UserProfile p = userProfileRepository.findByUserId(userId).orElse(null);
        return p != null ? p.getAvatarUrl() : null;
    }
}
