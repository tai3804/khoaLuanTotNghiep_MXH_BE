package iuh.fit.postservice.application.features.post.queries.get_all_posts;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetAllPostsQuery {
    BaseFilter filter;
    String cursor;
    UUID viewerId;
    /** Chỉ lấy bài người xem chưa xem (bảng tin trang chủ). Mặc định lấy tất cả. */
    boolean unseenOnly;
}
