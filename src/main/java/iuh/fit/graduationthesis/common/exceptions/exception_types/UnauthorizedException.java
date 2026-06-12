package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;

public class UnauthorizedException extends AppException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }
}
