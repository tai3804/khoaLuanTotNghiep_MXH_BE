package iuh.fit.postservice.application.service.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Danh sách bài viết đã xếp hạng cho một người xem (lưu trong Redis một thời gian ngắn).
 * Các trang của cùng một người cắt ra từ CÙNG một bản chụp nên cuộn xuống không bị trùng/lặp bài
 * dù điểm số của bài thay đổi giữa hai lần gọi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankedFeedSnapshot implements Serializable {
    private List<String> postIds = new ArrayList<>();
    private List<Double> scores = new ArrayList<>();
    private List<String> reasons = new ArrayList<>();
}
