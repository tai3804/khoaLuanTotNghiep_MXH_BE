package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EditMessageRequest {
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    String content;
}
