package iuh.fit.adminservice.application.features.user.commands.create_user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import iuh.fit.adminservice.application.dto.response.UserInfoResponse;
import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.infrastructure.feign.AuthFeignClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateUserCommandHandler {

    AuthFeignClient authFeignClient;
    AuditLogService auditLogService;
    ObjectMapper objectMapper = new ObjectMapper();

    public UserInfoResponse handle(Map<String, Object> request) {
        String email = (String) request.get("email");
        String role = request.get("role") != null ? (String) request.get("role") : "USER";
        log.info("Admin creating user: email={}, role={}", email, role);
        try {
            UserInfoResponse res = authFeignClient.createUserByAdmin(request);
            auditLogService.log("CREATE_USER", "USER", res.getId().toString(), "Tạo tài khoản mới: " + email + " với vai trò " + role);
            return res;
        } catch (FeignException e) {
            log.error("Feign exception creating user: status={}, content={}", e.status(), e.contentUTF8());
            String errorMessage = "Không thể tạo tài khoản, vui lòng kiểm tra lại.";
            try {
                String content = e.contentUTF8();
                if (content != null && !content.isBlank()) {
                    JsonNode node = objectMapper.readTree(content);
                    if (node.has("message")) {
                        errorMessage = node.get("message").asText();
                    } else if (node.has("error")) {
                        errorMessage = node.get("error").asText();
                    }
                }
            } catch (Exception ignored) {
            }

            if (errorMessage.contains("Email already exists") || (e.getMessage() != null && e.getMessage().contains("Email already exists"))) {
                errorMessage = "Địa chỉ email này đã tồn tại trong hệ thống.";
            } else if (errorMessage.contains("Username already exists") || (e.getMessage() != null && e.getMessage().contains("Username already exists"))) {
                errorMessage = "Tên đăng nhập (Username) này đã được sử dụng.";
            }

            throw new IllegalArgumentException(errorMessage);
        } catch (Exception e) {
            log.error("Failed to create user: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Không thể tạo tài khoản";
            if (msg.contains("Email already exists")) {
                msg = "Địa chỉ email này đã tồn tại trong hệ thống.";
            } else if (msg.contains("Username already exists")) {
                msg = "Tên đăng nhập (Username) này đã được sử dụng.";
            }
            throw new IllegalArgumentException(msg);
        }
    }
}
