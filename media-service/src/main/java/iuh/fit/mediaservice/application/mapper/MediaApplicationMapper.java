package iuh.fit.mediaservice.application.mapper;

import iuh.fit.mediaservice.application.features.media.commands.upload_media.UploadMediaResult;
import iuh.fit.mediaservice.domain.enums.MediaType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MediaApplicationMapper {

    @Mapping(target = "fileUrl", source = "fileUrl")
    @Mapping(target = "fileKey", source = "fileKey")
    @Mapping(target = "fileName", source = "fileName")
    @Mapping(target = "mediaType", source = "mediaType")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "contentType", source = "contentType")
    UploadMediaResult toResult(String fileUrl, String fileKey, String fileName, MediaType mediaType, long fileSize, String contentType);
}
