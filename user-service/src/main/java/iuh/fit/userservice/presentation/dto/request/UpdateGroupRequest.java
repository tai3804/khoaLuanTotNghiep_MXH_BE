package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRequest {
    @Size(min = 1, max = 100, message = "Tên nhóm phải từ 1 đến 100 ký tự")
    String name;
    @Size(max = 1000, message = "Mô tả nhóm tối đa 1000 ký tự")
    String description;
    @Size(max = 500, message = "Đường dẫn ảnh bìa tối đa 500 ký tự")
    String coverUrl;
    GroupPrivacy privacy;
    Boolean postApprovalRequired;
    @Size(max = 3000, message = "Nội quy nhóm tối đa 3000 ký tự")
    String rules;
}
