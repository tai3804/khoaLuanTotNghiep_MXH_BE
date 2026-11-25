package iuh.fit.authservice.application.features.auth;

/**
 * Tên khoá Redis của phiên xác thực hai bước. Bước đăng nhập (ghi) và bước xác minh (đọc) PHẢI dùng chung các hàm này.
 * Hai nơi từng tự viết chuỗi khoá riêng; khi bước đăng nhập đổi khoá mà bước xác minh không đổi theo thì mọi lần xác minh
 * 2FA đều báo mã không hợp lệ.
 */
public final class MfaSessionKeys {

    private MfaSessionKeys() {
    }

    /** Giá trị: mã người dùng (UUID dạng chuỗi). Có mặt nghĩa là phiên đăng nhập bước một còn hiệu lực. */
    public static String session(String mfaToken) {
        return "mfa_session:" + mfaToken;
    }

    /** Giá trị: loại xác thực ({@code TOTP} hoặc {@code EMAIL}). */
    public static String sessionType(String mfaToken) {
        return "mfa_session_type:" + mfaToken;
    }

    /** Giá trị: mã OTP đã gửi qua email (chỉ với loại EMAIL). */
    public static String emailOtp(String mfaToken) {
        return "mfa_email_otp:" + mfaToken;
    }

    /** Giá trị: số lần nhập sai mã của phiên này. */
    public static String attempts(String mfaToken) {
        return "mfa_attempts:" + mfaToken;
    }
}
