package iuh.fit.mediaservice.application.features.media.commands.upload_media;

import iuh.fit.mediaservice.application.mapper.MediaApplicationMapper;
import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.domain.enums.MediaType;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UploadMediaCommandHandler {

    AwsS3StorageService awsS3StorageService;
    MediaRepository mediaRepository;
    MediaApplicationMapper mediaApplicationMapper;

    @Transactional
    public UploadMediaResult handle(UploadMediaCommand command) {
        MultipartFile file = command.getFile();
        String fileUrl = awsS3StorageService.uploadFile(file, command.getFolder());
        String fileKey = awsS3StorageService.extractFileKeyFromUrl(fileUrl);
        MediaType mediaType = awsS3StorageService.determineMediaType(file.getContentType());

        UUID userId = command.getUserId();
        if (userId != null) {
            try {
                Media mediaEntity = mediaApplicationMapper.toEntity(
                        userId, fileKey, fileUrl, file.getOriginalFilename(), file.getSize(), mediaType, command.getFolder()
                );
                mediaRepository.save(mediaEntity);
                log.info("Saved Media metadata to media_db for fileKey: {}, userId: {}", fileKey, userId);
            } catch (Exception e) {
                log.error("Failed to save Media metadata to DB for fileKey {}: {}", fileKey, e.getMessage());
            }
        }

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
