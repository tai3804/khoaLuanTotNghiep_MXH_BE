package iuh.fit.mediaservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.mediaservice.application.features.media.commands.delete_media.DeleteMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.delete_media.DeleteMediaCommandHandler;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaCommandHandler;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaResult;
import iuh.fit.mediaservice.presentation.constants.ApiConstants;
import iuh.fit.mediaservice.presentation.constants.MessageConstants;
import iuh.fit.mediaservice.presentation.dto.request.UploadMediaRequest;
import iuh.fit.mediaservice.presentation.dto.request.UploadMultipleMediaRequest;
import iuh.fit.mediaservice.presentation.dto.response.MediaResponse;
import iuh.fit.mediaservice.presentation.mapper.MediaPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import iuh.fit.mediaservice.presentation.dto.response.PresignedUrlResponse;
import iuh.fit.mediaservice.presentation.dto.response.UserStorageQuotaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping(ApiConstants.MEDIA_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Media Management", description = "APIs for uploading and managing media files (images & videos) on AWS S3")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    AwsS3StorageService awsS3StorageService;
    MediaRepository mediaRepository;
    UploadMediaCommandHandler uploadMediaCommandHandler;
    DeleteMediaCommandHandler deleteMediaCommandHandler;
    MediaPresentationMapper mediaPresentationMapper;
    JwtUtil jwtUtil;

    @GetMapping("/quota")
    @Operation(summary = "Get user storage quota", description = "Retrieves current authenticated user's storage quota details", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserStorageQuotaResponse>> getUserQuota() {
        String userIdStr = jwtUtil.getCurrentUserId();
        UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
        long usedBytes = userId != null ? mediaRepository.sumFileSizeByUserId(userId) : 0;
        long maxQuotaBytes = 1073741824L; // 1 GB Quota

        UserStorageQuotaResponse response = mediaPresentationMapper.toQuotaResponse(userId, usedBytes, maxQuotaBytes);
        return ResponseEntity.ok(ApiResponse.success(response, "Storage quota retrieved successfully"));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user media gallery", description = "Retrieves paginated list of media files uploaded by user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<MediaResponse>>> getUserMediaGallery(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Media> mediaPage = mediaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<MediaResponse> responseList = mediaPage.getContent().stream()
                .map(mediaPresentationMapper::toResponse)
                .toList();

        PagedResponse<MediaResponse> pagedResponse = PagedResponse.<MediaResponse>builder()
                .content(responseList)
                .pageNumber(mediaPage.getNumber())
                .pageSize(mediaPage.getSize())
                .totalElements(mediaPage.getTotalElements())
                .totalPages(mediaPage.getTotalPages())
                .isLast(mediaPage.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "User media gallery retrieved successfully"));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "Generate S3 Presigned Upload URL", description = "Generates a temporary presigned URL for direct browser/client upload to S3", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestParam(required = false, defaultValue = "uploads") String folder,
            @RequestParam String fileName,
            @RequestParam(required = false, defaultValue = "application/octet-stream") String contentType,
            @RequestParam(required = false, defaultValue = "15") int durationMinutes) {
        PresignedUrlResponse response = awsS3StorageService.generatePresignedUploadUrl(folder, fileName, contentType, durationMinutes);
        return ResponseEntity.ok(ApiResponse.success(response, "Presigned URL generated successfully"));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload single media file to S3", description = "Uploads an image or video file to AWS S3 bucket", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MediaResponse>> uploadFile(@Valid @ModelAttribute UploadMediaRequest request) {
        UploadMediaCommand command = mediaPresentationMapper.toUploadCommand(request);
        UploadMediaResult result = uploadMediaCommandHandler.handle(command);
        MediaResponse response = mediaPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.FILE_UPLOADED_SUCCESSFULLY));
    }

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple media files to S3", description = "Uploads multiple image or video files to AWS S3 bucket", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<MediaResponse>>> uploadMultipleFiles(@Valid @ModelAttribute UploadMultipleMediaRequest request) {
        List<MediaResponse> responseList = new ArrayList<>();
        for (MultipartFile file : request.getFiles()) {
            UploadMediaCommand command = mediaPresentationMapper.toUploadCommand(file, request.getFolder());
            UploadMediaResult result = uploadMediaCommandHandler.handle(command);
            responseList.add(mediaPresentationMapper.toResponse(result));
        }
        return ResponseEntity.ok(ApiResponse.success(responseList, MessageConstants.FILES_UPLOADED_SUCCESSFULLY));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete file from S3", description = "Deletes a media file from AWS S3 by fileKey", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam String fileKey) {
        DeleteMediaCommand command = mediaPresentationMapper.toDeleteCommand(fileKey);
        deleteMediaCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.FILE_DELETED_SUCCESSFULLY));
    }
}
