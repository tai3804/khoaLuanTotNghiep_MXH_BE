package iuh.fit.mediaservice.application.features.media.queries.stream_media_file;

import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StreamMediaFileQueryHandler {

    AwsS3StorageService awsS3StorageService;

    public ResponseEntity<Resource> handle(String requestUri) {
        String prefix = "/api/v1/media/files/";
        String fileKey = "";
        int index = requestUri.indexOf(prefix);
        if (index != -1) {
            fileKey = requestUri.substring(index + prefix.length());
        }
        return awsS3StorageService.streamFile(fileKey);
    }
}
