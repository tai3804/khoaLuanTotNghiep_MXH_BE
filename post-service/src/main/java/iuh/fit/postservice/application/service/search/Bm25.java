package iuh.fit.postservice.application.service.search;

import java.util.List;

/**
 * Okapi BM25 (Robertson &amp; Zaragoza, "The Probabilistic Relevance Framework: BM25 and Beyond", 2009), biến thể IDF của
 * Lucene/Elasticsearch (luôn không âm):
 *
 * <pre>
 * idf(t)      = ln(1 + (N - df + 0,5) / (df + 0,5))
 * score(d, q) = Σ_t idf(t) · tf · (k1 + 1) / (tf + k1 · (1 - b + b · |d| / avgdl))
 * </pre>
 *
 * k1 = 1,2 (bão hoà tần suất), b = 0,75 (chuẩn hoá độ dài). Hàm thuần, không truy cập DB.
 */
public final class Bm25 {

    public static final double K1 = 1.2;
    public static final double B = 0.75;

    private Bm25() {
    }

    public static double idf(long totalDocs, long docFreq) {
        double n = Math.max(totalDocs, 1);
        double df = Math.min(Math.max(docFreq, 0), totalDocs);
        return Math.log(1.0 + (n - df + 0.5) / (df + 0.5));
    }

    /**
     * Số lần từ khoá {@code term} xuất hiện trong văn bản. Từ khoá cuối có thể khớp theo tiền tố
     * (tương ứng toán tử {@code term:*} của truy vấn full-text).
     */
    public static int termFrequency(List<String> docTokens, String term, boolean prefix) {
        int tf = 0;
        for (String token : docTokens) {
            if (prefix ? token.startsWith(term) : token.equals(term)) tf++;
        }
        return tf;
    }

    public static double termScore(double idf, int tf, int docLength, double avgDocLength) {
        if (tf <= 0) return 0.0;
        double avg = avgDocLength > 0 ? avgDocLength : Math.max(docLength, 1);
        double norm = tf + K1 * (1.0 - B + B * docLength / avg);
        return idf * tf * (K1 + 1.0) / norm;
    }

    /**
     * Điểm BM25 của một tài liệu. Từ khoá người dùng gõ CÓ dấu được khớp đúng dấu (so với {@code docExact}), nên "ôn" không
     * khớp "ổn"/"ơn"; từ khoá gõ không dấu khớp theo dạng đã bỏ dấu (so với {@code docFolded}). Độ dài tài liệu luôn tính theo
     * {@code docFolded}. df/idf lấy từ chỉ mục đã bỏ dấu nên là cận trên của df đúng dấu (idf hơi thấp hơn, an toàn).
     *
     * @param terms        từ khoá đã bỏ dấu
     * @param exactTerms   dạng có dấu tương ứng (bằng {@code terms} nếu người dùng gõ không dấu)
     * @param idfs         idf tương ứng từng từ khoá
     * @param prefixLast   từ khoá cuối khớp theo tiền tố
     */
    public static double score(List<String> docFolded, List<String> docExact, List<String> terms, List<String> exactTerms,
                               List<Double> idfs, boolean prefixLast, double avgDocLength) {
        double total = 0.0;
        for (int i = 0; i < terms.size(); i++) {
            boolean prefix = prefixLast && i == terms.size() - 1;
            boolean accented = !exactTerms.get(i).equals(terms.get(i));
            int tf = accented
                    ? termFrequency(docExact, exactTerms.get(i), prefix)
                    : termFrequency(docFolded, terms.get(i), prefix);
            total += termScore(idfs.get(i), tf, docFolded.size(), avgDocLength);
        }
        return total;
    }

    /**
     * Chuẩn hoá về [0,1] theo thang tuyệt đối của truy vấn: chia cho Σ idf, tức điểm của một tài liệu có đủ mọi từ khoá, mỗi từ
     * một lần, độ dài trung bình. Không chia cho điểm lớn nhất trong tập ứng viên, vì khi không bài nào khớp tốt thì một bài chỉ
     * khớp một từ rất phổ biến sẽ bị coi là khớp 100%.
     */
    public static double normalize(double bm25, List<Double> idfs) {
        double sum = 0.0;
        for (double v : idfs) sum += v;
        return sum <= 0 ? 0.0 : Math.min(1.0, bm25 / sum);
    }
}
