package iuh.fit.commonframework.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

/**
 * Phát bởi ai-service (topic "post.summarized") sau khi tạo bản tóm tắt cho bài viết dài.
 * post-service lưu bản tóm tắt cùng bài viết. contentLength dùng để bỏ qua bản tóm tắt cũ
 * nếu nội dung bài đã bị chỉnh sửa trong lúc AI đang xử lý.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostSummarizedEvent implements Serializable {
    UUID postId;
    String summary;
    int contentLength;
}
