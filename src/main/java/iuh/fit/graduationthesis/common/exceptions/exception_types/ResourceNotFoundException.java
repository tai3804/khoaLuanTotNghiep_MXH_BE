package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
