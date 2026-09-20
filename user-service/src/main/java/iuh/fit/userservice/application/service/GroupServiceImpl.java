package iuh.fit.userservice.application.service;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.domain.repository.GroupMemberRepository;
import iuh.fit.userservice.domain.repository.GroupRepository;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.mapper.GroupMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupServiceImpl implements GroupService {

    GroupRepository groupRepository;
    GroupMemberRepository groupMemberRepository;
    GroupMapper groupMapper;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID creatorId) {
        Group group = groupMapper.toEntity(request);
        group.setCreatorId(creatorId);
        group.setMemberCount(1);
        group = groupRepository.save(group);

        GroupMember creatorMember = GroupMember.builder()
                .groupId(group.getId())
                .userId(creatorId)
                .role(GroupRole.ADMIN)
                .status(GroupMemberStatus.APPROVED)
                .build();
        groupMemberRepository.save(creatorMember);

        return groupMapper.toResponse(group);
    }

    @Override
    public GroupResponse getGroupById(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND));
        return groupMapper.toResponse(group);
    }
}

