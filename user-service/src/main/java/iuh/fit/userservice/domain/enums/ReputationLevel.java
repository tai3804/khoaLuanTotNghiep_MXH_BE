package iuh.fit.userservice.domain.enums;

/**
 * Bậc uy tín theo điểm (0-100). Khai báo từ cao xuống thấp: bậc đầu tiên có minScore <= điểm là bậc hiện tại.
 */
public enum ReputationLevel {
    EXCELLENT(90, "Rất tốt"),
    GOOD(70, "Tốt"),
    CAUTION(50, "Cần chú ý"),
    WARNING(30, "Cảnh báo"),
    CRITICAL(0, "Nguy cơ cao");

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
        return CRITICAL;
    }
}
