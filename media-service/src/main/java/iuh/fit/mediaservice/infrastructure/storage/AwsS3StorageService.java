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

import iuh.fit.mediaservice.presentation.dto.response.PresignedUrlResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AwsS3StorageService {

    S3Client s3Client;
    S3Presigner s3Presigner;
    AwsS3Properties awsS3Properties;

    // Disallowed executable files for security
    static List<String> DISALLOWED_EXTENSIONS = List.of(
            ".exe", ".bat", ".cmd", ".sh", ".msi", ".dll", ".so", ".vbs", ".js", ".jar"
    );

    public PresignedUrlResponse generatePresignedUploadUrl(String folder, String fileName, String contentType, int durationMinutes) {
        String cleanFileName = (fileName != null && !fileName.isBlank()) ? fileName : "file.bin";
        String extension = getFileExtension(cleanFileName);
        validateExtension(extension);

        String fileKey = buildFileKey(folder, cleanFileName);
        String mimeType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        int validDuration = durationMinutes > 0 ? durationMinutes : 15;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsS3Properties.getBucketName())
                .key(fileKey)
                .contentType(mimeType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(validDuration))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedPutObjectRequest.url().toString();
        String fileUrl = buildFileUrl(fileKey);

        log.info("Generated S3 Presigned Upload URL for key: {}", fileKey);

        return PresignedUrlResponse.builder()
                .presignedUrl(presignedUrl)
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .expiresInMinutes(validDuration)
                .build();
    }

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

        byte[] uploadData;
        long uploadSize;

        try {
            if (contentType.toLowerCase().startsWith("image/") && !contentType.toLowerCase().contains("gif") && !contentType.toLowerCase().contains("svg")) {
                uploadData = compressImageIfLarge(file);
                uploadSize = uploadData.length;
                contentType = "image/jpeg";
            } else {
                uploadData = file.getBytes();
                uploadSize = file.getSize();
            }
        } catch (IOException e) {
            log.error("Failed to read file input stream", e);
            throw new BusinessException(MediaServiceErrorCode.FILE_UPLOAD_FAILED);
        }

        if (uploadSize > awsS3Properties.getMaxFileSizeBytes()) {
            throw new BusinessException(MediaServiceErrorCode.FILE_TOO_LARGE);
        }

        String fileKey = buildFileKey(folder, originalFilename);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsS3Properties.getBucketName())
                    .key(fileKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(uploadData));

            log.info("Successfully uploaded file to S3. Key: {}, Size: {} bytes", fileKey, uploadSize);
            return buildFileUrl(fileKey);

        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new BusinessException(MediaServiceErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private byte[] compressImageIfLarge(MultipartFile file) throws IOException {
        try {
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                return file.getBytes();
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            int maxDimension = 1920;

            int newWidth = width;
            int newHeight = height;

            if (width > maxDimension || height > maxDimension) {
                double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
                newWidth = (int) (width * scale);
                newHeight = (int) (height * scale);
            }

            // Create a clean TYPE_INT_RGB image with white background to handle transparency/PNG/colorspace correctly
            BufferedImage rgbImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImage.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, newWidth, newHeight);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                ImageIO.write(rgbImage, "jpg", baos);
                return baos.toByteArray();
            }

            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.82f); // 82% quality compression
                }
                writer.write(null, new IIOImage(rgbImage, null, null), param);
            } finally {
                writer.dispose();
            }

            byte[] compressedBytes = baos.toByteArray();
            log.info("Image auto-compressed from {} bytes to {} bytes", file.getSize(), compressedBytes.length);
            return compressedBytes;
        } catch (Exception e) {
            log.warn("Image compression failed ({}), falling back to raw file bytes", e.getMessage());
            return file.getBytes();
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
            try {
                java.net.URI uri = new java.net.URI(fileUrlOrKey);
                String path = uri.getPath();
                if (path != null && path.startsWith("/")) {
                    return path.substring(1);
                }
            } catch (Exception e) {
                int lastSlashIndex = fileUrlOrKey.lastIndexOf(".com/");
                if (lastSlashIndex != -1) {
                    return fileUrlOrKey.substring(lastSlashIndex + 5);
                }
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
        String cleanFolder = (folder != null && !folder.isBlank()) ? folder.trim() : "uploads";
        String uuid = UuidUtil.generateUuidV7().toString();
        String extension = getFileExtension(originalFilename);
        return String.format("%s/%s%s", cleanFolder, uuid, extension);
    }

    private String buildFileUrl(String fileKey) {
        String customDomain = awsS3Properties.getCustomDomain();
        if (customDomain != null && !customDomain.isBlank()) {
            String baseUrl = customDomain.endsWith("/") ? customDomain.substring(0, customDomain.length() - 1) : customDomain;
            return String.format("%s/%s", baseUrl, fileKey);
        }

        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                awsS3Properties.getBucketName(),
                awsS3Properties.getRegion(),
                fileKey);
    }
}
