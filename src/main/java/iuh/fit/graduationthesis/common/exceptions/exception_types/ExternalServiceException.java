package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;

public class ExternalServiceException extends AppException {

    public ExternalServiceException(ErrorCode errorCode) {
        super(errorCode);
    }
}
