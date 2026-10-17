package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

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

    Set<UUID> initialMemberIds;
}
