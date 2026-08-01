package iuh.fit.mediaservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@RestController
@RequestMapping(ApiConstants.MEDIA_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Media Management", description = "APIs for uploading and managing media files (images & videos) on AWS S3")
public class MediaController {

    UploadMediaCommandHandler uploadMediaCommandHandler;
    DeleteMediaCommandHandler deleteMediaCommandHandler;
    MediaPresentationMapper mediaPresentationMapper;

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
