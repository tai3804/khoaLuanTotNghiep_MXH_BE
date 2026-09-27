package iuh.fit.adminservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AdminServiceErrorCode implements BaseError {
    RESOURCE_NOT_FOUND(404, "Resource not found", 404),
    REPORT_NOT_FOUND(404, "Report not found", 404),
    REPORT_ALREADY_SUBMITTED(400, "You have already submitted a pending report for this item", 400),
    UNAUTHORIZED(401, "Unauthorized access", 401);

    int code;
    String message;
    int statusCode;
}
