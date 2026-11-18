package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.search.SearchScorer.Weights;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bộ trọng số đang dùng cho xếp hạng tìm kiếm. Mặc định là {@link Weights#DEFAULT}; sau khi học bằng
 * {@code SearchWeightTuner} (admin gọi {@code POST /api/v1/posts/admin/search-eval}) dán chuỗi 7 số vào cấu hình
 * {@code search.weights} (biến môi trường {@code SEARCH_WEIGHTS}) để áp dụng, không cần biên dịch lại.
 * Chuỗi sai định dạng thì quay về mặc định.
 */
@Component
public class SearchWeightsProvider {

    private final Weights weights;

    public SearchWeightsProvider(@Value("${search.weights:}") String csv) {
        this.weights = Weights.parse(csv).normalized();
    }

    public Weights current() {
        return weights;
    }
}
