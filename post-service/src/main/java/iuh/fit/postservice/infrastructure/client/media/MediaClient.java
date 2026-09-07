package iuh.fit.postservice.infrastructure.client.media;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.infrastructure.client.media.dto.MediaClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "media-service")
public interface MediaClient {

    @PostMapping(value = "/api/v1/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<MediaClientResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder
    );

    @PostMapping(value = "/api/v1/media/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<List<MediaClientResponse>> uploadMultipleFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false) String folder
    );

    @DeleteMapping("/api/v1/media/delete")
    ApiResponse<Void> deleteFile(@RequestParam("fileKey") String fileKey);
}
