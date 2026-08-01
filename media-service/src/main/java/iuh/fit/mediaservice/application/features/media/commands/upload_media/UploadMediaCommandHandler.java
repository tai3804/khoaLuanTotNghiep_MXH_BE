package iuh.fit.mediaservice.application.features.media.commands.upload_media;

import iuh.fit.mediaservice.application.mapper.MediaApplicationMapper;
import iuh.fit.mediaservice.domain.enums.MediaType;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UploadMediaCommandHandler {

    AwsS3StorageService awsS3StorageService;
    MediaApplicationMapper mediaApplicationMapper;

    public UploadMediaResult handle(UploadMediaCommand command) {
        MultipartFile file = command.getFile();
        String fileUrl = awsS3StorageService.uploadFile(file, command.getFolder());
        String fileKey = awsS3StorageService.extractFileKeyFromUrl(fileUrl);
        MediaType mediaType = awsS3StorageService.determineMediaType(file.getContentType());

        return mediaApplicationMapper.toResult(
                fileUrl,
                fileKey,
                file.getOriginalFilename(),
                mediaType,
                file.getSize(),
                file.getContentType()
        );
    }
}
