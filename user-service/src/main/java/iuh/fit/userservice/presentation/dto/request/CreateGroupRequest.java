package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateGroupRequest {

    @NotBlank(message = "Tên nhóm không được để trống")
    @Size(max = 100, message = "Tên nhóm tối đa 100 ký tự")
    String name;

    @Size(max = 1000, message = "Mô tả nhóm tối đa 1000 ký tự")
    String description;

    @Size(max = 500, message = "Đường dẫn ảnh bìa tối đa 500 ký tự")
    String coverUrl;

    @NotNull(message = "Quyền riêng tư không được để trống")
    GroupPrivacy privacy;

    Boolean postApprovalRequired;

    @Size(max = 3000, message = "Nội quy nhóm tối đa 3000 ký tự")
    String rules;

    @Size(max = 3, message = "Chỉ được đặt tối đa 3 câu hỏi tham gia")
    List<@Size(max = 200, message = "Mỗi câu hỏi tối đa 200 ký tự") String> membershipQuestions;

    Set<UUID> initialMemberIds;
}
