package iuh.fit.mediaservice.application.features.media.commands.delete_media;

import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteMediaCommandHandler {

    AwsS3StorageService awsS3StorageService;

    public void handle(DeleteMediaCommand command) {
        awsS3StorageService.deleteFile(command.getFileKey());
    }
}
