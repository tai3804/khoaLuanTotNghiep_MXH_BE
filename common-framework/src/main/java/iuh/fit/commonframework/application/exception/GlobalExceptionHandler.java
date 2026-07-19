package iuh.fit.commonframework.application.exception;

import iuh.fit.commonframework.application.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(value = Exception.class)
        public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
                log.error("Exception: ", exception);
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
