package iuh.fit.aiservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AiServiceErrorCode implements BaseError {
    RESOURCE_NOT_FOUND(404, "Resource not found", 404),
    /** Người dùng gửi tin nhắn rỗng tới trợ lý AI. */
    AI_ASSISTANT_EMPTY_MESSAGE(4001, "Tin nhắn gửi Trợ lý AI không được để trống.", 400),
    /** Không gọi được Gemini (thiếu khóa API, hết hạn mức, quá thời gian hoặc mô hình trả về rỗng). */
    AI_ASSISTANT_UNAVAILABLE(4002, "Không kết nối được tới mô hình AI (Gemini) nên Trợ lý AI chưa trả lời được. Vui lòng thử lại sau.", 400),
    /** Các tính năng AI khác (gợi ý caption, hashtag, viết lại, tóm tắt, dịch, phân tích...) không gọi được Gemini. */
    AI_MODEL_UNAVAILABLE(4003, "Không kết nối được tới mô hình AI (Gemini) nên chưa thực hiện được yêu cầu. Vui lòng thử lại sau.", 400),
    /** Nội dung đầu vào của tính năng AI bị rỗng. */
    AI_EMPTY_INPUT(4004, "Nội dung gửi tới AI không được để trống.", 400);

    int code;
    String message;
    int statusCode;
}
