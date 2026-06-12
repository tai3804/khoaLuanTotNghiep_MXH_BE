package iuh.fit.graduationthesis.common.exceptions;

import iuh.fit.graduationthesis.common.dto.responses.ApiResponse;
import iuh.fit.graduationthesis.common.exceptions.exception_types.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlobalExceptionHandler {

    MessageSource messageSource;

    /**
     * Resolve message từ MessageSource theo ngôn ngữ hiện tại (Accept-Language hoặc ?lang=)
     */
    private String resolveMessage(ErrorCode errorCode) {
        return messageSource.getMessage(
                errorCode.getMessageKey(),
                null,
                errorCode.getMessageKey(), // fallback nếu không tìm thấy key
                LocaleContextHolder.getLocale()
        );
    }

    /**
     * 1. Các lỗi nghiệp vụ tùy biến từ hệ thống (AppException)
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            AppException ex,
            HttpServletRequest request
    ) {
        log.info("[App Error] - Type: {}, Method: {}, URL: {}, Message: {}",
                ex.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorCode errorCode = ex.getErrorCode();
        String message = resolveMessage(errorCode);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode, request.getRequestURI(), message);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(apiResponse);
    }

    /**
     * 2. Lỗi Validation dữ liệu đầu vào (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        log.info("[Validation Error] - Method: {}, URL: {}", request.getMethod(), request.getRequestURI());

        // Thu thập và gộp lỗi nếu trùng field
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existingMessage, newMessage) -> existingMessage + ", " + newMessage
                ));

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        String message = resolveMessage(errorCode);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode, request.getRequestURI(), message, errors);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(apiResponse);
    }

    /**
     * 3. JSON sai định dạng (thiếu dấu ngoặc, sai kiểu dữ liệu, body rỗng...)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.info("[Malformed JSON] - Method: {}, URL: {}, Detail: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST_BODY;
        String message = resolveMessage(errorCode);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode, request.getRequestURI(), message);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(apiResponse);
    }

    /**
     * 4. Lỗi hệ thống không xác định (Bọc cuối cho NullPointerException, SQL...)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("[System Error] - Lỗi không xác định tại URL: {}, Method: {}",
                request.getRequestURI(), request.getMethod(), ex);

        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        String message = resolveMessage(errorCode);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode, request.getRequestURI(), message);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(apiResponse);
    }
}