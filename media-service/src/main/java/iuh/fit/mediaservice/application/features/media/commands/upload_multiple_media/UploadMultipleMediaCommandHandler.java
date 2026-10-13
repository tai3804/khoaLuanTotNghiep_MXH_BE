package iuh.fit.mediaservice.application.features.media.commands.upload_multiple_media;

import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaCommandHandler;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UploadMultipleMediaCommandHandler {

    UploadMediaCommandHandler uploadMediaCommandHandler;

    @Transactional
    public List<UploadMediaResult> handle(UploadMultipleMediaCommand command) {
        List<UploadMediaResult> results = new ArrayList<>();
        if (command.getFiles() == null || command.getFiles().isEmpty()) {
            return results;
        }

        for (MultipartFile file : command.getFiles()) {
            UploadMediaCommand singleCommand = UploadMediaCommand.builder()
                    .file(file)
                    .folder(command.getFolder())
                    .userId(command.getUserId())
                    .build();
            UploadMediaResult result = uploadMediaCommandHandler.handle(singleCommand);
            results.add(result);
        }

        return results;
    }
}
