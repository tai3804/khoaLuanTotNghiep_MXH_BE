package iuh.fit.userservice.application.service;

import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;

import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, UUID creatorId);
    GroupResponse getGroupById(UUID groupId);
}

