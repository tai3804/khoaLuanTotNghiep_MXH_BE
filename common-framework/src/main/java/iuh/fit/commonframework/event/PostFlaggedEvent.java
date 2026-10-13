package iuh.fit.commonframework.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

/**
 * Phát bởi ai-service (topic "post.flagged") khi AI nghi ngờ một bài viết vi phạm
 * và cần kiểm duyệt viên xem xét. admin-service nhận sự kiện này để đưa bài vào hàng chờ kiểm duyệt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostFlaggedEvent implements Serializable {
    UUID postId;
    UUID authorId;
    double score;
    String category;
    String severity;
    String suggestedAction;
    String reason;
    String keywords;
    String contentSnippet;
    boolean fallback;
}
