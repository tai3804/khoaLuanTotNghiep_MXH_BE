package iuh.fit.userservice.domain.enums;

/**
 * Bậc uy tín theo điểm (0-100). Khai báo từ cao xuống thấp: bậc đầu tiên có minScore <= điểm là bậc hiện tại.
 * <pre>
 * 90-100 Rất tốt | 80-89 Tốt | 50-79 Cần chú ý | 25-49 Yếu | 10-24 Rất yếu | dưới 10 Bị khóa (khóa tài khoản nhiều ngày)
 * </pre>
 * Quyền hạn và mức phạt của từng bậc nằm ở {@code ReputationPolicyProvider}.
 */
public enum ReputationLevel {
    EXCELLENT(90, "Rất tốt"),
    GOOD(80, "Tốt"),
    CAUTION(50, "Cần chú ý"),
    WEAK(25, "Yếu"),
    VERY_WEAK(10, "Rất yếu"),
    LOCKED(0, "Bị khóa");

    private final int minScore;
    private final String label;

    ReputationLevel(int minScore, String label) {
        this.minScore = minScore;
        this.label = label;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getLabel() {
        return label;
    }

    public static ReputationLevel of(int score) {
        for (ReputationLevel level : values()) {
            if (score >= level.minScore) {
                return level;
            }
        }
        return LOCKED;
    }
}
