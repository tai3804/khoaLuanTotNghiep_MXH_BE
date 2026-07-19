package iuh.fit.commonframework.application.exception;

public interface BaseError {
    int getCode();
    String getMessage();
    int getStatusCode();
}
