package iuh.fit.userservice.application.service;

/**
 * Ngưỡng cho tìm kiếm theo ý nghĩa (embedding) của người dùng và nhóm.
 *
 * <p>Vector Gemini (768 chiều, đã chuẩn hoá) dồn thành cụm: hai văn bản <b>không liên quan</b> vẫn có cosine khoảng 0,55–0,65.
 * Đo thực tế: truy vấn "biển", "Tai", "người thích lập trình"... so với hồ sơ chỉ có tên cho 0,55–0,655. Ngưỡng cũ 0,35 nằm
 * hẳn dưới mức nhiễu đó nên không lọc được gì: mọi truy vấn đều trả về người/nhóm đầu bảng dù chẳng liên quan.
 * 0,70 cao hơn mức nhiễu đã đo; chỉ những kết quả thật sự gần nghĩa mới vượt qua.
 */
public final class SemanticSearchPolicy {

    public static final double MIN_SIMILARITY = 0.70;

    private SemanticSearchPolicy() {
    }
}
