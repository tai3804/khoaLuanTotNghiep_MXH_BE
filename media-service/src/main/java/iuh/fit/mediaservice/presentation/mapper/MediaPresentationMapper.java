package iuh.fit.mediaservice.presentation.mapper;

import iuh.fit.mediaservice.application.features.media.commands.delete_media.DeleteMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaCommand;
import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaResult;
import iuh.fit.mediaservice.presentation.dto.request.UploadMediaRequest;
import iuh.fit.mediaservice.presentation.dto.response.MediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.web.multipart.MultipartFile;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MediaPresentationMapper {

    UploadMediaCommand toUploadCommand(UploadMediaRequest request);

    @Mapping(target = "folder", source = "folder")
    @Mapping(target = "file", source = "file")
    UploadMediaCommand toUploadCommand(MultipartFile file, String folder);

    DeleteMediaCommand toDeleteCommand(String fileKey);

    MediaResponse toResponse(UploadMediaResult result);
}
