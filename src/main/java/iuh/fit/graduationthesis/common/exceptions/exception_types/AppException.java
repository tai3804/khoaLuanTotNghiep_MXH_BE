package iuh.fit.graduationthesis.common.exceptions.exception_types;

import iuh.fit.graduationthesis.common.exceptions.ErrorCode;
import lombok.Getter;

@Getter
public abstract class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    protected AppException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
    }
}

