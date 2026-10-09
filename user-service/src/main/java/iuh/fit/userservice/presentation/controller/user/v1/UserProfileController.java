package iuh.fit.userservice.presentation.controller.user.v1;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
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
import iuh.fit.userservice.application.features.user_profile.queries.search_users.SearchUsersHandler;
import iuh.fit.userservice.application.features.user_profile.queries.search_users.SearchUsersQuery;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.domain.entities.ProfileViewHistory;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.enums.Gender;
import iuh.fit.userservice.domain.repository.ProfileViewHistoryRepository;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.constants.MessageConstants;
import iuh.fit.userservice.presentation.dto.request.UpdateProfessionalModeRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateUserProfileRequest;
import iuh.fit.userservice.presentation.dto.response.AudienceDemographicsResponse;
import iuh.fit.userservice.presentation.dto.response.ProfileVisitorResponse;
import iuh.fit.userservice.presentation.dto.response.UserProfileResponse;
import iuh.fit.userservice.presentation.mapper.UserProfilePresentationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping(ApiConstants.USER_API + "/profile")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Profile Management", description = "APIs for viewing, updating, and deleting user profiles")
public class UserProfileController {

    GetUserProfileQueryHandler getUserProfileQueryHandler;
    UpdateUserProfileCommandHandler updateUserProfileCommandHandler;
    DeleteUserProfileCommandHandler deleteUserProfileCommandHandler;
    SearchUsersHandler searchUsersHandler;
    UserProfilePresentationMapper userProfilePresentationMapper;
    UserProfileApplicationMapper userProfileApplicationMapper;
    UserProfileRepository userProfileRepository;
    ProfileViewHistoryRepository profileViewHistoryRepository;
    UserConnectionRepository userConnectionRepository;
    JwtUtil jwtUtil;

    @GetMapping("/search")
    @Operation(summary = "Search users by name", description = "Retrieves paginated list of user profiles matching the keyword query in BaseFilter", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> searchUsers(
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        SearchUsersQuery searchQuery = SearchUsersQuery.builder()
                .filter(filter)
                .build();
        PagedResponse<GetUserProfileResult> result = searchUsersHandler.handle(searchQuery);
        PagedResponse<UserProfileResponse> response = userProfilePresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(response, MessageConstants.USER_PROFILE_RETRIEVED_SUCCESSFULLY));
    }

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

    @PatchMapping("/professional-mode")
    @Operation(summary = "Toggle or update professional mode", description = "Updates creator mode and category for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfessionalMode(
            @Valid @RequestBody UpdateProfessionalModeRequest request) {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        UUID userId = UUID.fromString(userIdStr);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND));

        if (request.getIsProfessionalMode() != null) {
            profile.setIsProfessionalMode(request.getIsProfessionalMode());
        }
        if (request.getCreatorCategory() != null) {
            profile.setCreatorCategory(request.getCreatorCategory());
        }
        UserProfile saved = userProfileRepository.save(profile);
        UserProfileResponse response = userProfilePresentationMapper.toResponse(
                userProfileApplicationMapper.toQueryResult(saved));
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.PROFESSIONAL_MODE_UPDATED_SUCCESSFULLY));
    }

    @PostMapping("/{userId}/view")
    @Operation(summary = "Record profile view", description = "Increments profile view count when someone views another user's profile")
    public ResponseEntity<ApiResponse<Long>> recordProfileView(@PathVariable UUID userId) {
        String viewerIdStr = jwtUtil.getCurrentUserId();
        if (viewerIdStr != null && !viewerIdStr.isBlank()) {
            try {
                UUID viewerId = UUID.fromString(viewerIdStr);
                if (!viewerId.equals(userId)) {
                    var historyOpt = profileViewHistoryRepository.findByTargetUserIdAndViewerId(userId, viewerId);
                    if (historyOpt.isPresent()) {
                        ProfileViewHistory h = historyOpt.get();
                        h.setLastViewedAt(LocalDateTime.now());
                        profileViewHistoryRepository.save(h);
                    } else {
                        profileViewHistoryRepository.save(ProfileViewHistory.builder()
                                .targetUserId(userId)
                                .viewerId(viewerId)
                                .lastViewedAt(LocalDateTime.now())
                                .build());
                    }
                    userProfileRepository.incrementProfileViewCount(userId);
                }
            } catch (Exception ignored) {}
        }
        Long totalViews = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getProfileViewCount)
                .orElse(0L);
        return ResponseEntity.ok(ApiResponse.success(totalViews, MessageConstants.PROFILE_VIEW_RECORDED_SUCCESSFULLY));
    }

    @GetMapping("/visitors")
    @Operation(summary = "Get recent profile visitors", description = "Retrieves users who viewed the current user's profile recently", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<ProfileVisitorResponse>>> getProfileVisitors(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        UUID currentUserId = UUID.fromString(userIdStr);
        var pageable = PageRequest.of(page, size);
        var pageResult = profileViewHistoryRepository.findByTargetUserIdOrderByLastViewedAtDesc(currentUserId, pageable);

        List<ProfileVisitorResponse> visitors = pageResult.getContent().stream()
                .map(hist -> {
                    var pOpt = userProfileRepository.findByUserId(hist.getViewerId());
                    String fn = pOpt.map(UserProfile::getFirstName).orElse(null);
                    String ln = pOpt.map(UserProfile::getLastName).orElse(null);
                    String mn = pOpt.map(UserProfile::getMiddleName).orElse(null);
                    String av = pOpt.map(UserProfile::getAvatarUrl).orElse(null);
                    String fullName = Stream.of(ln, mn, fn)
                            .filter(s -> s != null && !s.isBlank())
                            .collect(Collectors.joining(" "));

                    boolean isFriend = userConnectionRepository.findAcceptedFriendship(currentUserId, hist.getViewerId()).isPresent();
                    boolean isFollowing = userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                            currentUserId, hist.getViewerId(), ConnectionType.FOLLOW).isPresent();

                    return ProfileVisitorResponse.builder()
                            .viewerId(hist.getViewerId())
                            .firstName(fn)
                            .lastName(ln)
                            .middleName(mn)
                            .fullName(fullName.isBlank() ? null : fullName)
                            .avatarUrl(av)
                            .lastViewedAt(hist.getLastViewedAt())
                            .isFriend(isFriend)
                            .isFollowing(isFollowing)
                            .build();
                }).toList();

        var paged = PagedResponse.<ProfileVisitorResponse>builder()
                .content(visitors)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.paged(paged, MessageConstants.PROFILE_VISITORS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/audience-insights")
    @Operation(summary = "Get audience demographics", description = "Retrieves audience demographic breakdowns for professional dashboard", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AudienceDemographicsResponse>> getAudienceInsights() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        UUID currentUserId = UUID.fromString(userIdStr);

        // 1. Gather all actual audience user IDs (viewers, friends, followers)
        Set<UUID> audienceIds = new HashSet<>();

        var viewHistories = profileViewHistoryRepository.findByTargetUserId(currentUserId);
        for (var v : viewHistories) {
            if (v.getViewerId() != null && !v.getViewerId().equals(currentUserId)) {
                audienceIds.add(v.getViewerId());
            }
        }

        var friendships = userConnectionRepository.findAllAcceptedFriendships(currentUserId);
        for (var f : friendships) {
            UUID other = f.getRequesterId().equals(currentUserId) ? f.getTargetId() : f.getRequesterId();
            if (other != null && !other.equals(currentUserId)) {
                audienceIds.add(other);
            }
        }

        var followers = userConnectionRepository.findAllAcceptedFollowers(currentUserId);
        for (var fl : followers) {
            if (fl.getRequesterId() != null && !fl.getRequesterId().equals(currentUserId)) {
                audienceIds.add(fl.getRequesterId());
            }
        }

        // 2. Fetch audience profiles strictly from database (NO mock fallback)
        List<UserProfile> audienceProfiles = audienceIds.isEmpty()
                ? Collections.emptyList()
                : userProfileRepository.findByUserIdIn(new ArrayList<>(audienceIds));

        long totalAudience = audienceProfiles.size();

        // 3. Compute real dynamic gender distribution based on actual profiles
        long maleCount = 0;
        long femaleCount = 0;
        long otherCount = 0;

        for (var p : audienceProfiles) {
            if (p.getGender() != null) {
                switch (p.getGender()) {
                    case MALE -> maleCount++;
                    case FEMALE -> femaleCount++;
                    case OTHER, PREFER_NOT_TO_SAY -> otherCount++;
                }
            }
        }

        long genderTotal = maleCount + femaleCount + otherCount;
        double malePct = genderTotal == 0 ? 0.0 : Math.round((maleCount * 1000.0) / genderTotal) / 10.0;
        double femalePct = genderTotal == 0 ? 0.0 : Math.round((femaleCount * 1000.0) / genderTotal) / 10.0;
        double otherPct = genderTotal == 0 ? 0.0 : Math.round((otherCount * 1000.0) / genderTotal) / 10.0;

        var genderBreakdown = AudienceDemographicsResponse.GenderBreakdownDto.builder()
                .malePercentage(malePct)
                .femalePercentage(femalePct)
                .otherPercentage(otherPct)
                .build();

        // 4. Compute real dynamic age distribution based on actual user date of birth
        long age13To17 = 0;
        long age18To24 = 0;
        long age25To34 = 0;
        long age35To44 = 0;
        long age45Plus = 0;
        long ageTotal = 0;

        LocalDate today = LocalDate.now();
        for (var p : audienceProfiles) {
            if (p.getDateOfBirth() != null) {
                int years = Period.between(p.getDateOfBirth(), today).getYears();
                if (years >= 13 && years <= 17) age13To17++;
                else if (years >= 18 && years <= 24) age18To24++;
                else if (years >= 25 && years <= 34) age25To34++;
                else if (years >= 35 && years <= 44) age35To44++;
                else if (years >= 45) age45Plus++;
                ageTotal++;
            }
        }

        double p13To17 = ageTotal == 0 ? 0.0 : Math.round((age13To17 * 1000.0) / ageTotal) / 10.0;
        double p18To24 = ageTotal == 0 ? 0.0 : Math.round((age18To24 * 1000.0) / ageTotal) / 10.0;
        double p25To34 = ageTotal == 0 ? 0.0 : Math.round((age25To34 * 1000.0) / ageTotal) / 10.0;
        double p35To44 = ageTotal == 0 ? 0.0 : Math.round((age35To44 * 1000.0) / ageTotal) / 10.0;
        double p45Plus = ageTotal == 0 ? 0.0 : Math.round((age45Plus * 1000.0) / ageTotal) / 10.0;

        List<AudienceDemographicsResponse.AgeRangeMetricDto> ageRanges = List.of(
                new AudienceDemographicsResponse.AgeRangeMetricDto("18-24 tuổi", p18To24),
                new AudienceDemographicsResponse.AgeRangeMetricDto("25-34 tuổi", p25To34),
                new AudienceDemographicsResponse.AgeRangeMetricDto("35-44 tuổi", p35To44),
                new AudienceDemographicsResponse.AgeRangeMetricDto("13-17 tuổi", p13To17),
                new AudienceDemographicsResponse.AgeRangeMetricDto("45+ tuổi", p45Plus)
        );

        // 5. Compute real dynamic location distribution strictly from user locations
        Map<String, Long> locationCounts = new HashMap<>();
        long totalWithLocation = 0;
        for (var p : audienceProfiles) {
            String loc = p.getLocation();
            if (loc != null && !loc.trim().isEmpty()) {
                String cleanLoc = loc.trim();
                locationCounts.put(cleanLoc, locationCounts.getOrDefault(cleanLoc, 0L) + 1);
                totalWithLocation++;
            }
        }

        final long denomLocation = totalWithLocation;
        List<AudienceDemographicsResponse.CityMetricDto> topCities = locationCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(5)
                .map(entry -> {
                    double pct = denomLocation == 0 ? 0.0 : Math.round((entry.getValue() * 1000.0) / denomLocation) / 10.0;
                    return AudienceDemographicsResponse.CityMetricDto.builder()
                            .city(entry.getKey())
                            .percentage(pct)
                            .build();
                })
                .toList();

        // 6. Compute real dynamic hourly activity distribution from database interaction timestamps
        int[] hourlyCounts = new int[24];
        for (var v : viewHistories) {
            if (v.getLastViewedAt() != null) {
                int h = v.getLastViewedAt().getHour();
                if (h >= 0 && h < 24) hourlyCounts[h]++;
            }
        }
        for (var f : friendships) {
            if (f.getCreatedAt() != null) {
                int h = f.getCreatedAt().getHour();
                if (h >= 0 && h < 24) hourlyCounts[h]++;
            }
        }
        for (var fl : followers) {
            if (fl.getCreatedAt() != null) {
                int h = fl.getCreatedAt().getHour();
                if (h >= 0 && h < 24) hourlyCounts[h]++;
            }
        }

        int maxHourCount = 0;
        for (int c : hourlyCounts) {
            if (c > maxHourCount) maxHourCount = c;
        }

        int[] sampleHours = {0, 3, 6, 9, 12, 15, 18, 21};
        List<AudienceDemographicsResponse.HourlyMetricDto> hourlyActivity = new ArrayList<>();
        for (int sh : sampleHours) {
            int score = maxHourCount == 0 ? 0 : (int) Math.round((hourlyCounts[sh] * 100.0) / maxHourCount);
            hourlyActivity.add(AudienceDemographicsResponse.HourlyMetricDto.builder()
                    .hour(sh + "h")
                    .activePercentage(score)
                    .build());
        }

        var resp = AudienceDemographicsResponse.builder()
                .totalAudience(totalAudience)
                .genderBreakdown(genderBreakdown)
                .ageRanges(ageRanges)
                .topCities(topCities)
                .hourlyActivity(hourlyActivity)
                .build();

        return ResponseEntity.ok(ApiResponse.success(resp, MessageConstants.AUDIENCE_INSIGHTS_RETRIEVED_SUCCESSFULLY));
    }
}
