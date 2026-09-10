package iuh.fit.mediaservice.application.features.media.commands.delete_media;

import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteMediaCommandHandler {

    AwsS3StorageService awsS3StorageService;
    MediaRepository mediaRepository;

    @Transactional
    public void handle(DeleteMediaCommand command) {
        awsS3StorageService.deleteFile(command.getFileKey());
        if (command.getFileKey() != null) {
            mediaRepository.deleteByFileKey(command.getFileKey());
        }
    }
}
