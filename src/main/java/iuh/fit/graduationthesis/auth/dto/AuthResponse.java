package iuh.fit.graduationthesis.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    String token;
    String refreshToken; // Chỉ dùng nội bộ để truyền từ Service → Controller, sẽ bị null trước khi trả về client
    String userName;
}
