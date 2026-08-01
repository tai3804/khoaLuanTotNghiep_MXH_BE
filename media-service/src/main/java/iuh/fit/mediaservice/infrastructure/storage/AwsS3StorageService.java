package iuh.fit.mediaservice.infrastructure.storage;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.util.UuidUtil;
import iuh.fit.mediaservice.application.exception.MediaServiceErrorCode;
import iuh.fit.mediaservice.domain.enums.MediaType;
import iuh.fit.mediaservice.infrastructure.config.AwsS3Properties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AwsS3StorageService {

    S3Client s3Client;
    AwsS3Properties awsS3Properties;

    // Disallowed executable files for security
    static List<String> DISALLOWED_EXTENSIONS = List.of(
            ".exe", ".bat", ".cmd", ".sh", ".msi", ".dll", ".so", ".vbs", ".js", ".jar"
    );

    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MediaServiceErrorCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        validateExtension(extension);

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        String fileKey = buildFileKey(folder, originalFilename);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsS3Properties.getBucketName())
                    .key(fileKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Successfully uploaded file to S3. Key: {}", fileKey);
            return buildFileUrl(fileKey);

        } catch (IOException e) {
            log.error("Failed to read file input stream", e);
            throw new BusinessException(MediaServiceErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new BusinessException(MediaServiceErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void deleteFile(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(awsS3Properties.getBucketName())
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file from S3. Key: {}", fileKey);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage(), e);
            throw new BusinessException(MediaServiceErrorCode.FILE_DELETE_FAILED);
        }
    }

    public MediaType determineMediaType(String contentType) {
        if (contentType == null) return MediaType.OTHER;
        String lowerType = contentType.toLowerCase();
        if (lowerType.startsWith("image/")) {
            return MediaType.IMAGE;
        }
        if (lowerType.startsWith("video/")) {
            return MediaType.VIDEO;
        }
        if (lowerType.startsWith("application/") || lowerType.startsWith("text/")) {
            return MediaType.DOCUMENT;
        }
        return MediaType.OTHER;
    }

    public String extractFileKeyFromUrl(String fileUrlOrKey) {
        if (fileUrlOrKey == null || fileUrlOrKey.isBlank()) {
            return fileUrlOrKey;
        }
        if (fileUrlOrKey.startsWith("http://") || fileUrlOrKey.startsWith("https://")) {
            int lastSlashIndex = fileUrlOrKey.lastIndexOf(".com/");
            if (lastSlashIndex != -1) {
                return fileUrlOrKey.substring(lastSlashIndex + 5);
            }
        }
        return fileUrlOrKey;
    }

    private void validateExtension(String extension) {
        if (extension != null && DISALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(MediaServiceErrorCode.INVALID_FILE_FORMAT);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String buildFileKey(String folder, String originalFilename) {
        String uuidV7 = UuidUtil.generateUuidV7().toString();
        String cleanFilename = "file";
        if (originalFilename != null && !originalFilename.isBlank()) {
            cleanFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        String fileNameWithUuid = uuidV7 + "_" + cleanFilename;

        if (folder != null && !folder.isBlank()) {
            String cleanFolder = folder.startsWith("/") ? folder.substring(1) : folder;
            if (!cleanFolder.endsWith("/")) {
                cleanFolder += "/";
            }
            return cleanFolder + fileNameWithUuid;
        }
        return fileNameWithUuid;
    }

    private String buildFileUrl(String fileKey) {
        if (awsS3Properties.getCustomDomain() != null && !awsS3Properties.getCustomDomain().isBlank()) {
            String domain = awsS3Properties.getCustomDomain().endsWith("/")
                    ? awsS3Properties.getCustomDomain().substring(0, awsS3Properties.getCustomDomain().length() - 1)
                    : awsS3Properties.getCustomDomain();
            return domain + "/" + fileKey;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                awsS3Properties.getBucketName(),
                awsS3Properties.getRegion(),
                fileKey);
    }
}
