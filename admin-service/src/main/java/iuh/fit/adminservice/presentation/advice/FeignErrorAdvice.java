package iuh.fit.adminservice.presentation.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Thao tác quản trị gọi sang service khác (khoá tài khoản, đổi vai trò, thu hồi phiên...) mà service đó trả lỗi 4xx (vd 404 không
 * tìm thấy người dùng, 400 dữ liệu sai): trả đúng mã và thông điệp đó cho trang quản trị thay vì 500 chung chung.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignErrorAdvice {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handle(FeignException e) {
        int status = e.status();
        if (status < 400 || status >= 500) {
            log.error("Downstream service call failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(ApiResponse.error(502, "Dịch vụ liên quan đang gặp sự cố, vui lòng thử lại sau"));
        }
        return ResponseEntity.status(status).body(ApiResponse.error(status, messageOf(e, status)));
    }

    private static String messageOf(FeignException e, int status) {
        try {
            String body = e.contentUTF8();
            if (body != null && !body.isBlank()) {
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("message")) return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // thân lỗi không phải JSON
        }
        return status == 404 ? "Không tìm thấy dữ liệu" : "Yêu cầu không hợp lệ";
    }
}
