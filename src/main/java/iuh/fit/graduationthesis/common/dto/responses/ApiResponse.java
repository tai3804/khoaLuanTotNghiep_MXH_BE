package iuh.fit.graduationthesis.common.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import iuh.fit.graduationthesis.common.exceptions.ErrorCode;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    boolean success;
    int status;          // Lưu mã số HTTP (ví dụ: 400, 404)
    String error;        // Tên lỗi từ Enum (ví dụ: INVALID_PASSWORD, USER_NOT_FOUND)
    String message;      // Câu thông báo thân thiện với người dùng (đã được dịch theo ngôn ngữ)
    String path;         // API bị lỗi (ví dụ: /api/v1/auth/login)
    T data;              // Dữ liệu trả về khi thành công
    Map<String, String> validationErrors; // Lỗi nhập liệu (nếu có)

    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * STATIC HELPER CHO LUỒNG THÀNH CÔNG
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * STATIC HELPER CHO LUỒNG LỖI (message đã được resolve từ MessageSource)
     */
    public static ApiResponse<Void> error(ErrorCode errorCode, String path, String resolvedMessage) {
        return ApiResponse.<Void>builder()
                .success(false)
                .status(errorCode.getStatus().value())
                .error(errorCode.name())
                .message(resolvedMessage)
                .path(path)
                .build();
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String path, String resolvedMessage, Map<String, String> validationErrors) {
        return ApiResponse.<Void>builder()
                .success(false)
                .status(errorCode.getStatus().value())
                .error(errorCode.name())
                .message(resolvedMessage)
                .path(path)
                .validationErrors(validationErrors)
                .build();
    }
}