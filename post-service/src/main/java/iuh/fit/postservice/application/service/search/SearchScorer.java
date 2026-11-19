package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer;

/**
 * Giai đoạn xếp hạng L2 của tìm kiếm bài viết. Hàm thuần, không truy cập DB.
 *
 * <pre>
 * relevance = (w_ns·ngữ_nghĩa + w_cụm·cụm_khớp + w_ht·hashtag) / Σw                      (mỗi đặc trưng ∈ [0,1])
 * prior     = (w_xh·xã_hội + w_tt·tương_tác + w_mới·độ_mới) / Σw                          (mỗi đặc trưng ∈ [0,1])
 * score     = relevance · ((1 − span) + span·prior) · trọng_số_uy_tín_tác_giả
 * </pre>
 *
 * Bộ trọng số {@link Weights} là <b>tham số học được</b>: mặc định là giá trị đặt tay, và {@code SearchWeightTuner} tìm bộ trọng
 * số tối đa hoá NDCG@k trên tập truy vấn có gán nhãn (xem {@code SearchEvaluator}).
 *
 * <ul>
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
 * điểm trong khoảng ×(1 − span) … ×1 giữa các bài đã liên quan.
 */
public final class SearchScorer {

    /**
     * Bộ trọng số của hàm xếp hạng. Ba trọng số đầu tạo nên relevance, ba trọng số kế tạo nên prior (mỗi nhóm được chuẩn hoá
     * về tổng 1 khi dùng), {@code span} là biên độ điều chỉnh của prior (0 = bỏ qua prior).
     */
    public record Weights(double semantic, double phrase, double hashtag,
                          double social, double engagement, double freshness, double span) {

        public static final Weights DEFAULT = new Weights(0.70, 0.20, 0.10, 0.50, 0.30, 0.20, 0.40);
        public static final int SIZE = 7;
        public static final double MAX_SPAN = 0.80;

        public Weights normalized() {
            double r = Math.max(0, semantic) + Math.max(0, phrase) + Math.max(0, hashtag);
            double p = Math.max(0, social) + Math.max(0, engagement) + Math.max(0, freshness);
            double rs = r <= 1e-12 ? 1 : r;
            double ps = p <= 1e-12 ? 1 : p;
            return new Weights(
                    r <= 1e-12 ? 1.0 : Math.max(0, semantic) / rs,
                    r <= 1e-12 ? 0.0 : Math.max(0, phrase) / rs,
                    r <= 1e-12 ? 0.0 : Math.max(0, hashtag) / rs,
                    p <= 1e-12 ? 0.0 : Math.max(0, social) / ps,
                    p <= 1e-12 ? 0.0 : Math.max(0, engagement) / ps,
                    p <= 1e-12 ? 1.0 : Math.max(0, freshness) / ps,
                    Math.min(MAX_SPAN, Math.max(0, span)));
        }

        public double[] toArray() {
            return new double[]{semantic, phrase, hashtag, social, engagement, freshness, span};
        }

        public static Weights fromArray(double[] a) {
            return new Weights(a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
        }

        /** Đọc từ chuỗi 7 số phân cách bằng dấu phẩy; sai định dạng thì trả về mặc định. */
        public static Weights parse(String csv) {
            if (csv == null || csv.isBlank()) return DEFAULT;
            String[] parts = csv.split(",");
            if (parts.length != SIZE) return DEFAULT;
            try {
                double[] a = new double[SIZE];
                for (int i = 0; i < SIZE; i++) a[i] = Double.parseDouble(parts[i].trim());
                return fromArray(a).normalized();
            } catch (NumberFormatException e) {
                return DEFAULT;
            }
        }

        public String toCsv() {
            double[] a = normalized().toArray();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format(java.util.Locale.ROOT, "%.3f", a[i]));
            }
            return sb.toString();
        }
    }

    /**
     * Sàn tuyệt đối của điểm liên quan (trước khi nhân với "prior"): dưới mức này thì không đáng hiển thị, dù truy vấn đó
     * không có kết quả nào khá hơn. Bổ sung cho ngưỡng tương đối {@link #RELATIVE_CUTOFF}, vốn luôn giữ kết quả đứng đầu.
     */
    public static final double MIN_RELEVANCE = 0.05;

    /**
     * Ngưỡng theo z-score của cosine (so với phân phối nền của truy vấn). Dữ liệu thật (Gemini 768 chiều, 12 truy vấn đo trên 96 bài): bài đứng đầu của truy vấn có nội dung
     * phù hợp đạt z từ 3,0 đến 8,6; truy vấn không có nội dung phù hợp ("bitcoin", "xin chao", "quantum physics") chỉ đạt z &lt;= 2,6.
     * Dưới Z_CANDIDATE không được đưa vào danh sách ứng viên vector; điểm ngữ nghĩa bằng 0 tại Z_FLOOR và 1 tại Z_FULL;
     * bài chỉ gần nghĩa (không khớp cụm hay hashtag) phải đạt SEMANTIC_ONLY_MIN_Z mới vào kết quả.
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
     * Cắt đuôi: giữ các mục có điểm &gt;= ratio × điểm cao nhất. Ngưỡng tuyệt đối không dùng được vì thang điểm của cosine
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

    public record Features(double semanticZ, double phrase, double hashtag,
                           double social, double engagement, double freshness) {
    }

    public static double semantic(double z) {
        return clamp((z - Z_FLOOR) / (Z_FULL - Z_FLOOR));
    }

    public static double relevance(Features f) {
        return relevance(f, Weights.DEFAULT);
    }

    public static double relevance(Features f, Weights w) {
        Weights n = w.normalized();
        return n.semantic() * semantic(f.semanticZ())
                + n.phrase() * clamp(f.phrase())
                + n.hashtag() * clamp(f.hashtag());
    }

    public static double prior(Features f, Weights w) {
        Weights n = w.normalized();
        return n.social() * clamp(f.social())
                + n.engagement() * clamp(f.engagement())
                + n.freshness() * clamp(f.freshness());
    }

    public static double score(Features f, double authorWeight) {
        return score(f, authorWeight, Weights.DEFAULT);
    }

    public static double score(Features f, double authorWeight, Weights w) {
        Weights n = w.normalized();
        return relevance(f, n) * ((1.0 - n.span()) + n.span() * prior(f, n)) * authorWeight;
    }

    /** Có đủ liên quan để xuất hiện trong kết quả không: khớp cụm/hashtag, hoặc đủ gần nghĩa. */
    public static boolean isRelevant(double phrase, double semanticZ, double hashtag) {
        return phrase > 0 || hashtag > 0 || semanticZ >= SEMANTIC_ONLY_MIN_Z;
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
    public static String reason(Features f, boolean friend, boolean following, boolean memberGroup) {
        boolean semanticHit = f.semanticZ() >= SEMANTIC_ONLY_MIN_Z;
        String base;
        if (f.phrase() >= PHRASE_EXACT && semanticHit) base = "Khớp cụm từ và ngữ nghĩa";
        else if (f.phrase() > 0 && semanticHit) base = "Khớp cụm từ (không dấu) và ngữ nghĩa";
        else if (f.hashtag() > 0) base = "Khớp hashtag";
        else if (f.phrase() > 0) base = "Khớp cụm từ";
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
