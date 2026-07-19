package iuh.fit.commonframework.application.exception;

import lombok.Getter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BusinessException extends RuntimeException {

    BaseError errorCode;

    public BusinessException(BaseError errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
