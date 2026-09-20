package iuh.fit.userservice.presentation.controller.user.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.UserPrivacySetting;
import iuh.fit.userservice.domain.repository.UserPrivacySettingRepository;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.dto.request.PrivacySettingsDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.USER_API + "/privacy")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Privacy Management", description = "APIs for user privacy preferences")
@SecurityRequirement(name = "bearerAuth")
public class UserPrivacyController {

    UserPrivacySettingRepository userPrivacySettingRepository;
    JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get user privacy settings")
    public ResponseEntity<ApiResponse<PrivacySettingsDto>> getPrivacySettings() {
        UUID userId = getCurrentUserId();
        UserPrivacySetting setting = userPrivacySettingRepository.findByUserId(userId)
                .orElseGet(() -> UserPrivacySetting.builder()
                        .userId(userId)
                        .defaultPostPrivacy("PUBLIC")
                        .friendRequestPrivacy("EVERYONE")
                        .friendListPrivacy("PUBLIC")
                        .searchPrivacy("EVERYONE")
                        .build());

        PrivacySettingsDto dto = PrivacySettingsDto.builder()
                .defaultPostPrivacy(setting.getDefaultPostPrivacy())
                .friendRequestPrivacy(setting.getFriendRequestPrivacy())
                .friendListPrivacy(setting.getFriendListPrivacy())
                .searchPrivacy(setting.getSearchPrivacy())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto, "Privacy settings retrieved successfully"));
    }

    @PutMapping
    @Operation(summary = "Update user privacy settings")
    public ResponseEntity<ApiResponse<PrivacySettingsDto>> updatePrivacySettings(@RequestBody PrivacySettingsDto request) {
        UUID userId = getCurrentUserId();
        UserPrivacySetting setting = userPrivacySettingRepository.findByUserId(userId)
                .orElseGet(() -> UserPrivacySetting.builder()
                        .userId(userId)
                        .build());

        if (request.getDefaultPostPrivacy() != null) {
            setting.setDefaultPostPrivacy(request.getDefaultPostPrivacy());
        }
        if (request.getFriendRequestPrivacy() != null) {
            setting.setFriendRequestPrivacy(request.getFriendRequestPrivacy());
        }
        if (request.getFriendListPrivacy() != null) {
            setting.setFriendListPrivacy(request.getFriendListPrivacy());
        }
        if (request.getSearchPrivacy() != null) {
            setting.setSearchPrivacy(request.getSearchPrivacy());
        }

        setting = userPrivacySettingRepository.save(setting);

        PrivacySettingsDto dto = PrivacySettingsDto.builder()
                .defaultPostPrivacy(setting.getDefaultPostPrivacy())
                .friendRequestPrivacy(setting.getFriendRequestPrivacy())
                .friendListPrivacy(setting.getFriendListPrivacy())
                .searchPrivacy(setting.getSearchPrivacy())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto, "Privacy settings updated successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return UUID.fromString(userIdStr);
    }
}
