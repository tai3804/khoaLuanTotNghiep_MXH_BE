package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;

public class BusinessException extends AppException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }
}
