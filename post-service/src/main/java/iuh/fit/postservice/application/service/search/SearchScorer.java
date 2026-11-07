package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer;

/**
 * Giai đoạn xếp hạng L2 của tìm kiếm bài viết. Hàm thuần, không truy cập DB.
 *
 * <pre>
 * relevance = 0,40·BM25chuẩn + 0,35·ngữ_nghĩa + 0,15·cụm_khớp + 0,10·hashtag          (mỗi đặc trưng ∈ [0,1])
 * prior     = 0,50·xã_hội + 0,30·tương_tác + 0,20·độ_mới                                 (mỗi đặc trưng ∈ [0,1])
 * score     = relevance · (0,6 + 0,4·prior) · trọng_số_uy_tín_tác_giả
 * </pre>
 *
 * <ul>
 *   <li><b>BM25chuẩn</b> = min(1, BM25 / Σ idf) (xem {@link Bm25#normalize}): thang tuyệt đối theo truy vấn, không phụ thuộc tập ứng viên.</li>
 *   <li><b>ngữ_nghĩa</b> = clamp((z − 2) / (6 − 2), 0, 1), với z là z-score của cosine (embedding Gemini 768 chiều) so với phân
 *       phối cosine của <i>chính truy vấn đó</i> trên một mẫu ngẫu nhiên của corpus (xem {@code PostEmbeddingService.QueryBaseline}).</li>
 *   <li><b>cụm_khớp</b> = 1,0 nếu nội dung chứa nguyên cụm truy vấn đúng dấu; 0,6 nếu chỉ khớp sau khi bỏ dấu; 0 nếu không.</li>
 *   <li><b>xã_hội</b> = (bạn bè 3,0 | đang theo dõi 2,0 | của chính mình 0,5; + nhóm mình tham gia 1,5; + tương tác với tác giả
 *       trong 30 ngày, tối đa 3,0) / 7,5. Dùng lại đúng hằng số của bảng tin.</li>
 *   <li><b>tương_tác</b> = e / (e + 4) với e = ln(1 + thích + 2·bình luận + 3·chia sẻ).</li>
 *   <li><b>độ_mới</b> = 0,5 ^ (tuổi bài / 30 ngày). Tìm kiếm ít nhạy với thời gian hơn bảng tin (nửa đời 24 giờ).</li>
 * </ul>
 *
 * Relevance là cổng chính: một bài không liên quan không thể lên đầu nhờ bạn bè hay nhiều lượt thích; "prior" chỉ điều chỉnh
 * điểm trong khoảng ×0,6 … ×1,0 giữa các bài đã liên quan. Các trọng số này đặt tay (chưa có log nhấp chuột để huấn luyện
 * learning-to-rank), xem tài liệu thuật toán tìm kiếm.
 */
public final class SearchScorer {

    public static final double W_BM25 = 0.40;
    public static final double W_SEMANTIC = 0.35;
    public static final double W_PHRASE = 0.15;
    public static final double W_HASHTAG = 0.10;

    public static final double W_SOCIAL = 0.50;
    public static final double W_ENGAGEMENT = 0.30;
    public static final double W_FRESHNESS = 0.20;

    /**
     * Sàn tuyệt đối của điểm liên quan (trước khi nhân với "prior"): dưới 5% tổng trọng số của khớp từ khoá + ngữ nghĩa thì
     * không đáng hiển thị, dù truy vấn đó không có kết quả nào khá hơn (ví dụ chỉ khớp các âm tiết quá phổ biến như "đầu", "tư").
     * Bổ sung cho ngưỡng tương đối {@link #RELATIVE_CUTOFF}, vốn luôn giữ kết quả đứng đầu.
     */
    public static final double MIN_RELEVANCE = 0.05;

    public static final double PRIOR_FLOOR = 0.6;
    public static final double PRIOR_SPAN = 0.4;

    /**
     * Ngưỡng theo z-score của cosine (so với phân phối nền của truy vấn). Dữ liệu thật (Gemini 768 chiều, 12 truy vấn đo trên 96 bài): bài đứng đầu của truy vấn có nội dung
     * phù hợp đạt z từ 3,0 đến 8,6; truy vấn không có nội dung phù hợp ("bitcoin", "xin chao", "quantum physics") chỉ đạt z &lt;= 2,6.
     * Dưới Z_CANDIDATE không được đưa vào danh sách ứng viên vector; điểm ngữ nghĩa bằng 0 tại Z_FLOOR và 1 tại Z_FULL;
     * bài chỉ gần nghĩa (không khớp từ khoá) phải đạt SEMANTIC_ONLY_MIN_Z mới vào kết quả.
     */
    public static final double Z_CANDIDATE = 2.0;
    public static final double Z_FLOOR = 2.0;
    public static final double Z_FULL = 6.0;
    public static final double SEMANTIC_ONLY_MIN_Z = 2.8;

    public static final double FRESHNESS_HALF_LIFE_HOURS = 24.0 * 30;
    public static final double PHRASE_EXACT = 1.0;
    public static final double PHRASE_FOLDED = 0.6;

    private static final double MAX_SOCIAL_AFFINITY =
            PostRelevanceScorer.FRIEND + PostRelevanceScorer.MEMBER_GROUP + PostRelevanceScorer.INTERACTION_CAP;


    /** Kết quả có điểm thấp hơn {@code ratio} lần điểm của kết quả đứng đầu bị loại (cắt ngưỡng tương đối). */
    public static final double RELATIVE_CUTOFF = 0.35;

    /**
     * Cắt đuôi: giữ các mục có điểm &gt;= ratio × điểm cao nhất. Ngưỡng tuyệt đối không dùng được vì thang điểm của BM25/cosine
     * thay đổi theo từng truy vấn; so với kết quả tốt nhất thì truy vấn hiếm gặp vẫn trả đủ kết quả, còn truy vấn không có
     * nội dung phù hợp không bị lấp đầy bằng các bài chỉ "hơi giống".
     *
     * @param sortedDescending điểm đã sắp xếp giảm dần
     * @return số phần tử đầu danh sách được giữ lại
     */
    public static int cutoffIndex(double[] sortedDescending, double ratio) {
        if (sortedDescending.length == 0) return 0;
        double min = sortedDescending[0] * ratio;
        int n = 0;
        while (n < sortedDescending.length && sortedDescending[n] >= min) n++;
        return n;
    }

    private SearchScorer() {
    }

    public record Features(double bm25Norm, double semanticZ, double phrase, double hashtag,
                           double social, double engagement, double freshness) {
    }

    public static double semantic(double z) {
        return clamp((z - Z_FLOOR) / (Z_FULL - Z_FLOOR));
    }

    public static double relevance(Features f) {
        return W_BM25 * clamp(f.bm25Norm())
                + W_SEMANTIC * semantic(f.semanticZ())
                + W_PHRASE * clamp(f.phrase())
                + W_HASHTAG * clamp(f.hashtag());
    }

    public static double prior(Features f) {
        return W_SOCIAL * clamp(f.social())
                + W_ENGAGEMENT * clamp(f.engagement())
                + W_FRESHNESS * clamp(f.freshness());
    }

    public static double score(Features f, double authorWeight) {
        return relevance(f) * (PRIOR_FLOOR + PRIOR_SPAN * prior(f)) * authorWeight;
    }

    /** Có đủ liên quan để xuất hiện trong kết quả không: khớp từ khoá/hashtag, hoặc đủ gần nghĩa. */
    public static boolean isRelevant(double rawBm25, double semanticZ, double hashtag) {
        return rawBm25 > 0 || hashtag > 0 || semanticZ >= SEMANTIC_ONLY_MIN_Z;
    }

    public static double social(boolean own, boolean friend, boolean following, boolean memberGroup, int interactions30d) {
        double a = 0;
        if (friend) a += PostRelevanceScorer.FRIEND;
        else if (following) a += PostRelevanceScorer.FOLLOWING;
        else if (own) a += PostRelevanceScorer.OWN;
        if (memberGroup) a += PostRelevanceScorer.MEMBER_GROUP;
        a += Math.min(PostRelevanceScorer.INTERACTION_CAP, 1.2 * Math.log(1.0 + Math.max(0, interactions30d)));
        return clamp(a / MAX_SOCIAL_AFFINITY);
    }

    public static double engagement(long likes, long comments, long shares) {
        double e = PostRelevanceScorer.engagement(likes, comments, shares);
        return e / (e + 4.0);
    }

    public static double freshness(double ageHours) {
        return Math.pow(0.5, Math.max(0, ageHours) / FRESHNESS_HALF_LIFE_HOURS);
    }

    /** Lý do hiển thị cho người dùng: đặc trưng nào làm bài này lên cao. */
    public static String reason(Features f, boolean lexicalHit, boolean friend, boolean following, boolean memberGroup) {
        boolean semanticHit = f.semanticZ() >= SEMANTIC_ONLY_MIN_Z;
        String base;
        if (lexicalHit && semanticHit) base = "Khớp từ khóa và ngữ nghĩa";
        else if (lexicalHit) base = "Khớp từ khóa";
        else base = "Gần nghĩa với truy vấn";
        if (friend) return base + " · từ bạn bè";
        if (following) return base + " · từ người bạn theo dõi";
        if (memberGroup) return base + " · trong nhóm của bạn";
        return base;
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : Math.min(v, 1.0);
    }
}
