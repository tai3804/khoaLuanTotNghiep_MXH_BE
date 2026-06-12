package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;

public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
