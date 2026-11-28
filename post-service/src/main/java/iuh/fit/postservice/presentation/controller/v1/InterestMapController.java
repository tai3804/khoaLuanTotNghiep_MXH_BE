package iuh.fit.postservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.service.insight.InterestMapService;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.POST_API + "/interest-map")
@RequiredArgsConstructor
@Tag(name = "Interest Map", description = "Bản đồ sở thích: vector sở thích 768 chiều của người dùng chiếu xuống 2 chiều")
@SecurityRequirement(name = "bearerAuth")
public class InterestMapController {

    private final InterestMapService interestMapService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Bản đồ sở thích của tôi",
            description = "Vector sở thích (tâm có trọng số các bài đã thích/bình luận/chia sẻ) chiếu PCA cùng các bài viết và cụm chủ đề")
    public ResponseEntity<ApiResponse<InterestMapService.InterestMap>> myInterestMap() {
        String userId = jwtUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.success(interestMapService.build(UUID.fromString(userId)), "Interest map built"));
    }
}
