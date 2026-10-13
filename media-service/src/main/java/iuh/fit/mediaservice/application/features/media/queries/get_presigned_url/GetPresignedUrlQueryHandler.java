package iuh.fit.mediaservice.application.features.media.queries.get_presigned_url;

import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import iuh.fit.mediaservice.presentation.dto.response.PresignedUrlResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPresignedUrlQueryHandler {

    AwsS3StorageService awsS3StorageService;

    public PresignedUrlResponse handle(GetPresignedUrlQuery query) {
        String folder = query.getFolder() != null && !query.getFolder().isBlank() ? query.getFolder() : "uploads";
        String contentType = query.getContentType() != null && !query.getContentType().isBlank() ? query.getContentType() : "application/octet-stream";
        int duration = query.getDurationMinutes() > 0 ? query.getDurationMinutes() : 15;

        return awsS3StorageService.generatePresignedUploadUrl(folder, query.getFileName(), contentType, duration);
    }
}
