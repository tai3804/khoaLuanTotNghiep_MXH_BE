package iuh.fit.userservice.presentation.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class JoinGroupRequest {
    /** Câu trả lời theo thứ tự câu hỏi của nhóm (bắt buộc khi nhóm riêng tư có đặt câu hỏi). */
    List<String> answers;
}
