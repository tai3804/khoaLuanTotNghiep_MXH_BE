package iuh.fit.mediaservice.presentation.controller.internal;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.mediaservice.application.features.media.commands.delete_media.DeleteMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.delete_media.DeleteMediaCommandHandler;
import iuh.fit.mediaservice.presentation.constants.MessageConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/media")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalMediaController {

    DeleteMediaCommandHandler deleteMediaCommandHandler;

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> deleteInternalFile(@RequestParam("fileKey") String fileKey) {
        deleteMediaCommandHandler.handle(DeleteMediaCommand.builder().fileKey(fileKey).build());
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.FILE_DELETED_SUCCESSFULLY));
    }
}

