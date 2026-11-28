package iuh.fit.postservice.application.service.insight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Nhãn chủ đề trên bản đồ sở thích")
class InterestMapLabelTest {

    @Test
    @DisplayName("Bỏ ký hiệu/emoji và phần [..] ở đầu bài đại diện")
    void stripsLeadingSymbols() {
        assertEquals("hi", InterestMapService.labelFrom("⏹ [ĐÃ KẾT THÚC] hi"));
        assertEquals("Buổi sáng chạy bộ", InterestMapService.labelFrom("🏃‍♂️  Buổi sáng\nchạy bộ"));
        assertEquals("iuh xin chào", InterestMapService.labelFrom("#iuh xin chào"));
        assertEquals("test123", InterestMapService.labelFrom("#test123"));
    }

    @Test
    @DisplayName("Cắt nhãn dài còn tối đa 32 ký tự")
    void truncates() {
        String label = InterestMapService.labelFrom("Hôm nay mình hoàn thành chương ba của khoá luận tốt nghiệp rồi");
        assertTrue(label.length() <= 32, label);
        assertTrue(label.endsWith("…"));
    }

    @Test
    @DisplayName("Bài chỉ có ký hiệu thì vẫn có nhãn")
    void onlySymbols() {
        assertEquals("⏹", InterestMapService.labelFrom("⏹"));
    }
}
