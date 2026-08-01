package iuh.fit.mediaservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum MediaServiceErrorCode implements BaseError {
    INVALID_FILE_FORMAT(400, "Invalid media file format", 400),
    FILE_TOO_LARGE(400, "File size exceeds limit", 400),
    FILE_UPLOAD_FAILED(500, "Failed to upload file to AWS S3", 500),
    FILE_DELETE_FAILED(500, "Failed to delete file from AWS S3", 500),
    FILE_EMPTY(400, "Uploaded file cannot be empty", 400),
    UNAUTHORIZED(401, "Unauthorized access", 401);

    int code;
    String message;
    int statusCode;
}
