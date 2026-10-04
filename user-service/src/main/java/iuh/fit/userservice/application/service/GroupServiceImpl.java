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
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.mapper.GroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final GroupMapper groupMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID creatorId) {
        Group group = groupMapper.toEntity(request);
        group.setCreatorId(creatorId); group.setMemberCount(1);
        group.setPostApprovalRequired(Boolean.TRUE.equals(request.getPostApprovalRequired())); group.setRules(request.getRules());
        group = groupRepository.save(group);
        saveMember(group.getId(), creatorId, GroupRole.ADMIN, GroupMemberStatus.APPROVED);
        if (request.getInitialMemberIds() != null) {
            Set<UUID> invited = new HashSet<>(request.getInitialMemberIds()); invited.remove(creatorId);
            UUID groupId = group.getId();
            invited.forEach(id -> saveMember(groupId, id, GroupRole.MEMBER, GroupMemberStatus.APPROVED));
            group.setMemberCount(1 + invited.size()); groupRepository.save(group);

            String creatorName = getProfileFullName(creatorId);
            String creatorAvatar = getProfileAvatar(creatorId);
            for (UUID id : invited) {
                sendGroupNotification(id, creatorId, "GROUP_INVITE", "Lời mời tham gia nhóm", creatorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, creatorAvatar);
            }
        }
        return toResponse(group, creatorId);
    }

    @Override @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID groupId, UUID viewerId) { return toResponse(findGroup(groupId), viewerId); }
    @Override @Transactional(readOnly = true)
    public List<GroupResponse> getGroups(UUID viewerId) { return groupRepository.findAll().stream().map(g -> toResponse(g, viewerId)).toList(); }

    @Override @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId) {
        Group group = findGroup(groupId); requireAdmin(groupId, currentUserId);
        if (request.getName() != null && !request.getName().isBlank()) group.setName(request.getName().trim());
        if (request.getDescription() != null) group.setDescription(request.getDescription());
        if (request.getCoverUrl() != null) group.setCoverUrl(request.getCoverUrl());
        if (request.getPrivacy() != null) group.setPrivacy(request.getPrivacy());
        if (request.getPostApprovalRequired() != null) group.setPostApprovalRequired(request.getPostApprovalRequired());
        if (request.getRules() != null) group.setRules(request.getRules());
        return toResponse(groupRepository.save(group), currentUserId);
    }

    @Override @Transactional
    public void deleteGroup(UUID groupId, UUID currentUserId) { Group group = findGroup(groupId); requireAdmin(groupId, currentUserId); group.setDeleted(true); groupRepository.save(group); }

    @Override @Transactional
    public GroupResponse joinGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroup(groupId); GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);
        if (member != null && member.getStatus() == GroupMemberStatus.BANNED) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        if (member != null && member.getStatus() == GroupMemberStatus.PENDING) {
            groupMemberRepository.delete(member);
            return toResponse(group, currentUserId);
        }
        GroupMemberStatus status = group.getPrivacy() == GroupPrivacy.PUBLIC ? GroupMemberStatus.APPROVED : GroupMemberStatus.PENDING;
        if (member == null) { saveMember(groupId, currentUserId, GroupRole.MEMBER, status); if (status == GroupMemberStatus.APPROVED) increment(group, 1); }
        else if (member.getStatus() != GroupMemberStatus.APPROVED) { member.setStatus(status); groupMemberRepository.save(member); if (status == GroupMemberStatus.APPROVED) increment(group, 1); }

        if (status == GroupMemberStatus.PENDING) {
            String requesterName = getProfileFullName(currentUserId);
            String requesterAvatar = getProfileAvatar(currentUserId);
            List<GroupMember> admins = groupMemberRepository.findAllByGroupId(groupId).stream()
                    .filter(m -> m.getStatus() == GroupMemberStatus.APPROVED && (m.getRole() == GroupRole.ADMIN || m.getRole() == GroupRole.MODERATOR))
                    .toList();
            for (GroupMember admin : admins) {
                sendGroupNotification(admin.getUserId(), currentUserId, "GROUP_JOIN_REQUEST", "Yêu cầu tham gia nhóm", requesterName + " đã gửi yêu cầu tham gia nhóm \"" + group.getName() + "\".", groupId, requesterAvatar);
            }
        }

        return toResponse(group, currentUserId);
    }

    @Override @Transactional
    public void leaveGroup(UUID groupId, UUID currentUserId) {
        Group group = findGroup(groupId);
        if (group.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        }
        GroupMember member = getMember(groupId, currentUserId);
        if (member.getStatus() == GroupMemberStatus.APPROVED) increment(group, -1);
        groupMemberRepository.delete(member);
    }

    @Override @Transactional
    public GroupResponse reviewMember(UUID groupId, UUID memberId, boolean approved, UUID currentUserId) {
        requireModerator(groupId, currentUserId); Group group = findGroup(groupId);
        GroupMember member = getMember(groupId, memberId);
        if (member.getStatus() == GroupMemberStatus.PENDING && approved) {
            member.setStatus(GroupMemberStatus.APPROVED);
            increment(group, 1);
            String reviewerName = getProfileFullName(currentUserId);
            String reviewerAvatar = getProfileAvatar(currentUserId);
            sendGroupNotification(memberId, currentUserId, "GROUP_JOIN_ACCEPT", "Yêu cầu tham gia nhóm", reviewerName + " đã phê duyệt yêu cầu tham gia nhóm \"" + group.getName() + "\" của bạn.", groupId, reviewerAvatar);
        }
        else if (member.getStatus() == GroupMemberStatus.PENDING) member.setStatus(GroupMemberStatus.REJECTED);
        groupMemberRepository.save(member); return toResponse(group, currentUserId);
    }

    @Override @Transactional
    public GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId) {
        requireModerator(groupId, currentUserId); Group group = findGroup(groupId); long added = 0;
        Set<UUID> newlyAdded = new HashSet<>();
        for (UUID id : new HashSet<>(memberIds)) {
            GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, id).orElse(null);
            if (member == null) {
                saveMember(groupId, id, GroupRole.MEMBER, GroupMemberStatus.APPROVED);
                added++;
                newlyAdded.add(id);
            }
            else if (member.getStatus() != GroupMemberStatus.APPROVED && member.getStatus() != GroupMemberStatus.BANNED) {
                member.setStatus(GroupMemberStatus.APPROVED);
                groupMemberRepository.save(member);
                added++;
                newlyAdded.add(id);
            }
        }
        if (added > 0) increment(group, added);

        String actorName = getProfileFullName(currentUserId);
        String actorAvatar = getProfileAvatar(currentUserId);
        for (UUID id : newlyAdded) {
            sendGroupNotification(id, currentUserId, "GROUP_INVITE", "Lời mời tham gia nhóm", actorName + " đã thêm bạn vào nhóm \"" + group.getName() + "\".", groupId, actorAvatar);
        }

        return toResponse(group, currentUserId);
    }

    @Override @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(UUID groupId, UUID currentUserId, boolean includePending) {
        Group group = findGroup(groupId); GroupMember me = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId).orElse(null);
        if (group.getPrivacy() == GroupPrivacy.PRIVATE && (me == null || me.getStatus() != GroupMemberStatus.APPROVED)) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        boolean canReview = me != null && (me.getRole() == GroupRole.ADMIN || me.getRole() == GroupRole.MODERATOR);
        return groupMemberRepository.findAllByGroupId(groupId).stream().filter(m -> m.getStatus() == GroupMemberStatus.APPROVED || (includePending && canReview && m.getStatus() == GroupMemberStatus.PENDING)).map(this::toMemberResponse).toList();
    }

    @Override @Transactional
    public void changeRole(UUID groupId, UUID memberId, GroupRole role, UUID currentUserId) {
        requireAdmin(groupId, currentUserId); Group group = findGroup(groupId); GroupMember member = getMember(groupId, memberId);
        if (member.getUserId().equals(group.getCreatorId())) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        member.setRole(role); groupMemberRepository.save(member);

        String actorName = getProfileFullName(currentUserId);
        String actorAvatar = getProfileAvatar(currentUserId);
        String roleStr = role == GroupRole.ADMIN ? "Quản trị viên" : role == GroupRole.MODERATOR ? "Người kiểm duyệt" : "Thành viên";
        sendGroupNotification(memberId, currentUserId, "GROUP_ROLE_CHANGE", "Cập nhật vai trò nhóm", actorName + " đã thay đổi vai trò của bạn trong nhóm \"" + group.getName() + "\" thành " + roleStr + ".", groupId, actorAvatar);
    }

    @Override @Transactional
    public void removeMember(UUID groupId, UUID memberId, boolean ban, UUID currentUserId) {
        GroupMember actor = requireModerator(groupId, currentUserId); Group group = findGroup(groupId); GroupMember member = getMember(groupId, memberId);
        if (member.getUserId().equals(group.getCreatorId())) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        // A group moderator cannot remove an administrator; this protects the role hierarchy.
        if (member.getRole() == GroupRole.ADMIN && actor.getRole() != GroupRole.ADMIN) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        if (member.getStatus() == GroupMemberStatus.APPROVED) increment(group, -1);
        if (ban) {
            member.setStatus(GroupMemberStatus.BANNED);
            groupMemberRepository.save(member);
            String actorAvatar = getProfileAvatar(currentUserId);
            sendGroupNotification(memberId, currentUserId, "SYSTEM", "Thông báo từ nhóm", "Bạn đã bị cấm khỏi nhóm \"" + group.getName() + "\".", groupId, actorAvatar);
        } else {
            groupMemberRepository.delete(member);
        }
    }

    private Group findGroup(UUID id) { return groupRepository.findById(id).orElseThrow(() -> new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND)); }
    private GroupMember getMember(UUID groupId, UUID userId) { return groupMemberRepository.findByGroupIdAndUserId(groupId, userId).orElseThrow(() -> new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND)); }
    private void increment(Group group, long delta) { group.setMemberCount(Math.max(0, group.getMemberCount() + delta)); groupRepository.save(group); }
    private void saveMember(UUID groupId, UUID userId, GroupRole role, GroupMemberStatus status) { groupMemberRepository.save(GroupMember.builder().groupId(groupId).userId(userId).role(role).status(status).build()); }
    private GroupMember requireModerator(UUID groupId, UUID userId) { GroupMember m = getMember(groupId, userId); if (m.getStatus() != GroupMemberStatus.APPROVED || (m.getRole() != GroupRole.ADMIN && m.getRole() != GroupRole.MODERATOR)) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED); return m; }
    private void requireAdmin(UUID groupId, UUID userId) { if (requireModerator(groupId, userId).getRole() != GroupRole.ADMIN) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED); }
    private GroupResponse toResponse(Group group, UUID viewerId) {
        GroupResponse r = groupMapper.toResponse(group); GroupMember me = viewerId == null ? null : groupMemberRepository.findByGroupIdAndUserId(group.getId(), viewerId).orElse(null);
        r.setOwnerId(group.getCreatorId()); r.setMember(me != null && me.getStatus() == GroupMemberStatus.APPROVED); r.setAdmin(r.isMember() && me.getRole() == GroupRole.ADMIN); r.setModerator(r.isMember() && me.getRole() == GroupRole.MODERATOR); r.setJoinStatus(me == null ? null : me.getStatus());
        r.setMemberIds(groupMemberRepository.findAllByGroupIdAndStatus(group.getId(), GroupMemberStatus.APPROVED).stream().map(GroupMember::getUserId).toList()); return r;
    }
    private GroupMemberResponse toMemberResponse(GroupMember m) {
        UserProfile p = userProfileRepository.findByUserId(m.getUserId()).orElse(null); String name = p == null ? "Thành viên" : String.join(" ", Arrays.asList(p.getLastName(), p.getMiddleName(), p.getFirstName()).stream().filter(Objects::nonNull).filter(v -> !v.isBlank()).toList());
        return GroupMemberResponse.builder().userId(m.getUserId()).name(name).avatarUrl(p == null ? null : p.getAvatarUrl()).role(m.getRole()).status(m.getStatus()).joinedAt(m.getJoinedAt()).build();
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
        String name = String.join(" ", Arrays.asList(p.getLastName(), p.getMiddleName(), p.getFirstName()).stream().filter(Objects::nonNull).filter(v -> !v.isBlank()).toList()).trim();
        return name.isBlank() ? "Một người dùng" : name;
    }

    private String getProfileAvatar(UUID userId) {
        if (userId == null) return null;
        UserProfile p = userProfileRepository.findByUserId(userId).orElse(null);
        return p != null ? p.getAvatarUrl() : null;
    }

    @Override
    public List<GroupFeedVisibilityResponse> getFeedVisibility(List<UUID> groupIds, UUID viewerId) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        Set<UUID> requested = new LinkedHashSet<>(groupIds);
        Map<UUID, Group> groups = groupRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(Group::getId, group -> group));
        Map<UUID, GroupMember> memberships = groupMemberRepository.findAllByUserIdAndStatus(viewerId, GroupMemberStatus.APPROVED)
                .stream().collect(Collectors.toMap(GroupMember::getGroupId, member -> member, (left, right) -> left));
        return requested.stream().map(groupId -> {
            Group group = groups.get(groupId);
            return GroupFeedVisibilityResponse.builder()
                    .groupId(groupId)
                    .member(memberships.containsKey(groupId))
                    .publicGroup(group != null && group.getPrivacy() == GroupPrivacy.PUBLIC)
                    .role(memberships.containsKey(groupId) ? memberships.get(groupId).getRole() : null)
                    .postApprovalRequired(group != null && group.isPostApprovalRequired())
                    .build();
        }).toList();
    }
}

