package iuh.fit.commonframework.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    int code;
    String message;
    T data;

    // Flattened Pagination Metadata (only rendered when not null)
    Integer page;
    Integer size;
    Long totalElements;
    Integer totalPages;
    Boolean last;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Tạo success response (kèm data).
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo success response (kèm data và message).
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo success response phân trang phẳng từ PagedResponse DTO.
     * data chính là List<T> mảng dữ liệu trực tiếp cho UI.
     */
    public static <T> ApiResponse<List<T>> paged(PagedResponse<T> pagedResponse, String message) {
        if (pagedResponse == null) {
            return success(List.of(), message);
        }
        return ApiResponse.<List<T>>builder()
                .code(200)
                .message(message)
                .data(pagedResponse.getContent())
                .page(pagedResponse.getPage() + 1)
                .size(pagedResponse.getSize())
                .totalElements(pagedResponse.getTotalElements())
                .totalPages(pagedResponse.getTotalPages())
                .last(pagedResponse.isLast())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo success response phân trang phẳng (Flattened Paged Response).
     * data chính là List<T> mảng dữ liệu trực tiếp cho UI.
     */
    public static <T> ApiResponse<List<T>> paged(List<T> data, Page<?> page, String message) {
        return ApiResponse.<List<T>>builder()
                .code(200)
                .message(message)
                .data(data)
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo success response phân trang phẳng với tham số thủ công.
     */
    public static <T> ApiResponse<List<T>> paged(List<T> data, int page, int size, long totalElements, int totalPages, boolean last, String message) {
        return ApiResponse.<List<T>>builder()
                .code(200)
                .message(message)
                .data(data)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo error response.
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
