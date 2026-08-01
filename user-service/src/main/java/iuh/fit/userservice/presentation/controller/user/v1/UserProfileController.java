package iuh.fit.userservice.presentation.controller.user.v1;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.features.user_profile.commands.delete_user_profile.DeleteUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.delete_user_profile.DeleteUserProfileCommandHandler;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommandHandler;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileResult;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileQuery;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileQueryHandler;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.constants.MessageConstants;
import iuh.fit.userservice.presentation.dto.request.UpdateUserProfileRequest;
import iuh.fit.userservice.presentation.dto.response.UserProfileResponse;
import iuh.fit.userservice.presentation.mapper.UserProfilePresentationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.USER_API + "/profile")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Profile Management", description = "APIs for viewing, updating, and deleting user profiles")
public class UserProfileController {

    GetUserProfileQueryHandler getUserProfileQueryHandler;
    UpdateUserProfileCommandHandler updateUserProfileCommandHandler;
    DeleteUserProfileCommandHandler deleteUserProfileCommandHandler;
    UserProfilePresentationMapper userProfilePresentationMapper;
    JwtUtil jwtUtil;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieves profile details of the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }

        GetUserProfileQuery query = GetUserProfileQuery.builder()
                .userId(UUID.fromString(userIdStr))
                .build();

        GetUserProfileResult result = getUserProfileQueryHandler.handle(query);
        UserProfileResponse response = userProfilePresentationMapper.toResponse(result);

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.USER_PROFILE_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user profile by ID", description = "Retrieves public profile details of a specific user by ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfileByUserId(@PathVariable UUID userId) {
        GetUserProfileQuery query = GetUserProfileQuery.builder()
                .userId(userId)
                .build();

        GetUserProfileResult result = getUserProfileQueryHandler.handle(query);
        UserProfileResponse response = userProfilePresentationMapper.toResponse(result);

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.USER_PROFILE_RETRIEVED_SUCCESSFULLY));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates profile details of the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }

        UpdateUserProfileCommand command = userProfilePresentationMapper.toCommand(request);
        command.setUserId(UUID.fromString(userIdStr));

        UpdateUserProfileResult result = updateUserProfileCommandHandler.handle(command);
        UserProfileResponse response = userProfilePresentationMapper.toResponse(result);

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.USER_PROFILE_UPDATED_SUCCESSFULLY));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete current user profile", description = "Deletes the profile of the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteMyProfile() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }

        DeleteUserProfileCommand command = DeleteUserProfileCommand.builder()
                .userId(UUID.fromString(userIdStr))
                .build();

        deleteUserProfileCommandHandler.handle(command);

        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.USER_PROFILE_DELETED_SUCCESSFULLY));
    }
}
