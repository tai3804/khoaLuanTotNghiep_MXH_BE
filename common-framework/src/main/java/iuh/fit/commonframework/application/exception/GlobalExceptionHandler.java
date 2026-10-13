package iuh.fit.commonframework.application.exception;

import iuh.fit.commonframework.application.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(value = Exception.class)
        public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
                log.error("Exception: ", exception);

                // Unwrap BusinessException if wrapped inside nested exceptions
                Throwable cause = exception.getCause();
                while (cause != null) {
                        if (cause instanceof BusinessException be) {
                                return handlingBusinessException(be);
                        }
                        cause = cause.getCause();
                }

                String msg = exception.getMessage();
                if (msg != null && !msg.isBlank() && !msg.contains("Exception") && msg.length() < 200) {
                        return ResponseEntity.status(500).body(ApiResponse.error(500, msg));
                }

                ApiResponse<Void> apiResponse = ApiResponse.error(
                                ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(),
                                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
                return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(apiResponse);
        }

        @ExceptionHandler(value = BusinessException.class)
        public ResponseEntity<ApiResponse<Void>> handlingBusinessException(BusinessException exception) {
                BaseError errorCode = exception.getErrorCode();
                ApiResponse<Void> apiResponse = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
                return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
        }

        @ExceptionHandler(value = AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
                ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
                return ResponseEntity.status(errorCode.getStatusCode()).body(
                                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
        }

        @ExceptionHandler(value = NoResourceFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handlingNoResourceFoundException(NoResourceFoundException exception) {
                return ResponseEntity.status(404).body(
                                ApiResponse.error(404, "Resource not found: " + exception.getResourcePath()));
        }


        /**
         * Lỗi do dữ liệu client gửi lên (JSON sai định dạng, giá trị enum không tồn tại, thiếu/sai kiểu tham số,
         * sortBy trỏ tới trường không có) phải là 400 chứ không phải lỗi hệ thống 500.
         */
        @ExceptionHandler(value = { HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
                        MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
                        PropertyReferenceException.class })
        public ResponseEntity<ApiResponse<Void>> handlingBadRequest(Exception exception) {
                log.warn("Bad request: {}", exception.getMessage());
                ErrorCode errorCode = ErrorCode.INVALID_INPUT;
                return ResponseEntity.status(errorCode.getStatusCode()).body(
                                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
        }

        @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handlingUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
                ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                return ResponseEntity.status(errorCode.getStatusCode()).body(
                                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
        }

        @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handlingMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
                ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
                return ResponseEntity.status(errorCode.getStatusCode()).body(
                                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
        }

        @ExceptionHandler(value = ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handlingConstraintViolation(
                        ConstraintViolationException exception) {
                Map<String, String> errors = new HashMap<>();
                exception.getConstraintViolations()
                                .forEach(violation -> errors.put(violation.getPropertyPath().toString(),
                                                violation.getMessage()));

                ErrorCode errorCode = ErrorCode.INVALID_INPUT;

                ApiResponse<Map<String, String>> apiResponse = ApiResponse.<Map<String, String>>builder()
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .data(errors)
                                .build();

                return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
        }

        @ExceptionHandler(value = MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handlingValidation(
                        MethodArgumentNotValidException exception) {
                Map<String, String> errors = new HashMap<>();
                exception.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

                ErrorCode errorCode = ErrorCode.INVALID_INPUT;

                ApiResponse<Map<String, String>> apiResponse = ApiResponse.<Map<String, String>>builder()
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .data(errors)
                                .build();

                return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
        }
}
