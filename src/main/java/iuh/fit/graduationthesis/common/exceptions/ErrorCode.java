package iuh.fit.graduationthesis.common.exceptions;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//day la file dinh nghia cac loi, tat ca cac loi de trong nay
// messageKey tương ứng với key trong file i18n/messages_xx.properties
public enum ErrorCode {
    // --- Validation & Client Errors ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation_failed"),
    INVALID_KEY(HttpStatus.BAD_REQUEST, "error.invalid_key"),
    USER_EXISTED(HttpStatus.BAD_REQUEST, "error.user_existed"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user_not_found"),
    USERNAME_INVALID(HttpStatus.BAD_REQUEST, "error.username_invalid"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "error.invalid_password"),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "error.invalid_request_body"),

    // --- Security ---
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "error.unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "error.forbidden"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "error.unauthenticated"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.invalid_credentials"),

    // --- Infrastructure / External ---
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "error.external_service_error"),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.database_error"),

    // --- System ---
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "error.uncategorized_exception");

    HttpStatus status;
    String messageKey; // Key trong file messages_xx.properties (thay vì hardcoded message)
}