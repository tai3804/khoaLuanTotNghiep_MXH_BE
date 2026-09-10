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

import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.presentation.dto.response.UserStorageQuotaResponse;
import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MediaPresentationMapper {

    UploadMediaCommand toUploadCommand(UploadMediaRequest request);

    @Mapping(target = "folder", source = "folder")
    @Mapping(target = "file", source = "file")
    UploadMediaCommand toUploadCommand(MultipartFile file, String folder);

    DeleteMediaCommand toDeleteCommand(String fileKey);

    MediaResponse toResponse(UploadMediaResult result);

    MediaResponse toResponse(Media media);

    default UserStorageQuotaResponse toQuotaResponse(UUID userId, long usedBytes, long maxQuotaBytes) {
        double usedPercentage = maxQuotaBytes > 0 ? (double) usedBytes / maxQuotaBytes * 100 : 0;
        return UserStorageQuotaResponse.builder()
                .userId(userId)
                .usedBytes(usedBytes)
                .maxQuotaBytes(maxQuotaBytes)
                .usedPercentage(Math.round(usedPercentage * 100.0) / 100.0)
                .usedReadable(formatBytes(usedBytes))
                .maxQuotaReadable(formatBytes(maxQuotaBytes))
                .build();
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
