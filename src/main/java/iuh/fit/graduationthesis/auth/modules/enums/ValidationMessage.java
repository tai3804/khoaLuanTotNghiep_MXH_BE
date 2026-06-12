package iuh.fit.graduationthesis.auth.modules.enums; // Đúng package thư mục

import lombok.Getter;

@Getter
public enum ValidationMessage {
    USERNAME_NOT_EMPTY(Constants.USERNAME_NOT_EMPTY),
    PASSWORD_NOT_EMPTY(Constants.PASSWORD_NOT_EMPTY),
    ROLE_NOT_EMPTY(Constants.ROLE_NOT_EMPTY),
    PERMISSION_NOT_EMPTY(Constants.PERMISSION_NOT_EMPTY),
    USER_ID_NOT_NULL(Constants.USER_ID_NOT_NULL);

    private final String message;

    ValidationMessage(String message) {
        this.message = message;
    }

    // Dùng {key} để Bean Validation tự resolve từ MessageSource
    public static class Constants {
        public static final String USERNAME_NOT_EMPTY = "{validation.username.not_blank}";
        public static final String PASSWORD_NOT_EMPTY = "{validation.password.not_blank}";
        public static final String ROLE_NOT_EMPTY = "{validation.role.not_empty}";
        public static final String PERMISSION_NOT_EMPTY = "{validation.permission.not_empty}";
        public static final String USER_ID_NOT_NULL = "{validation.user_id.not_null}";
    }
}