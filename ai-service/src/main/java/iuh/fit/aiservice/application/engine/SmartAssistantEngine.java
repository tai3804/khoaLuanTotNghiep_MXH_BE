package iuh.fit.aiservice.application.engine;

import iuh.fit.aiservice.application.dto.MessageSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmartAssistantEngine {

    private final VietnameseTextNormalizer normalizer;

    /**
     * Phản hồi trò chuyện thông minh theo ngữ cảnh người dùng hỏi
     */
    public String generateChatReply(String message) {
        return generateChatReply(message, null);
    }

    /**
     * Phản hồi trò chuyện thông minh có ghi nhớ lịch sử đối thoại (multi-turn conversation)
     */
    public String generateChatReply(String message, List<?> history) {
        if (message == null || message.isBlank()) {
            return "Chào bạn! 👋 Tôi là **Trợ lý AI KLTN Social**. Bạn cần tôi hỗ trợ viết caption, gợi ý ý tưởng, giải đáp thắc mắc lập trình hay học tập?";
        }

        String lower = message.toLowerCase().trim();
        String normalized = normalizer.normalize(lower);

        // 1. Chào hỏi & Nhận diện danh tính
        if (containsAny(normalized, "xin chao", "hello", "hi ban", "alo", "chao ban", "hi ai", "hey", "good morning", "good evening") ||
            (lower.equals("hi") || lower.equals("chao") || lower.equals("hey"))) {
            return getDynamicGreeting(lower);
        }

        if (containsAny(normalized, "ban la ai", "ten gi", "gioi thieu ve ban", "chuc nang cua ban", "tro ly gi", "ai gi")) {
            return """
                    🤖 **Tôi là Trợ lý AI KLTN Social** — trợ lý ảo đồng hành cùng bạn trên mạng xã hội sinh viên!
                    
                    💡 **Những việc tôi có thể hỗ trợ bạn ngay:**
                    - ✍️ **Sáng tạo nội dung:** Viết caption lôi cuốn, tạo status bắt trend, gợi ý hashtag viral, viết bio cá nhân ấn tượng.
                    - 💻 **Lập trình & Kỹ thuật:** Tư vấn kiến trúc Microservices, Spring Boot, React, Kafka, Docker, gỡ lỗi code và giải thuật.
                    - 🎓 **Học tập & Khóa luận:** Hướng dẫn làm đồ án, mẹo bảo vệ trước hội đồng phản biện, phương pháp học tập Pomodoro & Active Recall.
                    - 📩 **Tóm tắt tin nhắn & Cuộc trò chuyện:** Tổng hợp nhanh nội dung tin nhắn chưa đọc kèm phân tích hình ảnh đính kèm!
                    
                    Bạn đang quan tâm đến chủ đề nào? Hãy cứ thoải mái chia sẻ nhé! ✨
                    """;
        }

        // 2. Bảo vệ đồ án / Khóa luận tốt nghiệp (KLTN)
        if (containsAny(normalized, "bao ve do an", "bao ve khoa luan", "do an tot nghiep", "khoa luan tot nghiep", "bao ve kltn", "hoi dong phan bien", "hoi dong khoa luan")) {
            return """
                    🎓 **Bí kíp 5 bước bảo vệ Khóa luận / Đồ án tốt nghiệp đạt điểm tối đa:**
                    
                    1. 📑 **Báo cáo chuẩn mực:**
                       - Kiểm tra kỹ danh mục hình vẽ, bảng biểu, trích dẫn tài liệu tham khảo theo chuẩn APA/IEEE. Căn lề lề trên 2.5cm, dưới 2.5cm, trái 3.5cm, phải 2.0cm.
                    
                    2. 🖥️ **Slide súc tích (15 - 20 slide):**
                       - *Cấu trúc vàng:* Đặt vấn đề (2 slide) ➔ Yêu cầu & Kiến trúc hệ thống (4 slide) ➔ Công nghệ cốt lõi (3 slide) ➔ Kết quả đạt được & Demo (6 slide) ➔ Kết luận & Hướng phát triển (2 slide).
                    
                    3. ⏱️ **Kỹ năng thuyết trình (Canh chuẩn 12-15 phút):**
                       - Giọng nói dõng dạc, tự tin, mắt nhìn bao quát hội đồng. Nhấn mạnh vào điểm sáng tạo và giải pháp bạn đã giải quyết được.
                    
                    4. 🛡️ **Kịch bản Demo dự phòng (Fail-safe):**
                       - Chuẩn bị sẵn 1 video quay màn hình chất lượng cao phòng trường hợp mạng hội trường hoặc thiết bị chập chờn.
                    
                    5. 🎯 **Bình tĩnh khi phản biện:**
                       - Chuẩn bị sổ tay ghi lại trọn vẹn câu hỏi của thầy cô. Trả lời đúng trọng tâm, trung thực: điều gì đã làm được thì trình bày rõ ràng, điều gì chưa làm được thì chân thành tiếp thu để phát triển trong tương lai.
                    
                    ✨ *Chúc bạn có một buổi bảo vệ khóa luận thành công rực rỡ và ghi trọn điểm 10!*
                    """;
        }

        // 3. Gợi ý Caption bài viết
        if (containsAny(normalized, "caption", "viet stt", "stt", "dang bai", "status") ||
            (containsAny(normalized, "goi y", "viet cho toi", "tao cho toi") && containsAny(normalized, "nang luong", "hoc tap", "ngay moi", "hai huoc", "tam trang"))) {
            return generateCaptionsForChat(normalized);
        }

        // 4. Gợi ý Hashtag
        if (containsAny(normalized, "hashtag", "hash tag", "the tag", "tag pho bien", "tag thinh hanh")) {
            return generateHashtagsForChat(normalized);
        }

        // 5. Ý tưởng đăng bài cho nhóm / fanpage
        if (containsAny(normalized, "chu de thao luan", "y tuong dang bai", "dang len nhom", "post group", "nhom sinh vien", "tang tuong tac")) {
            return """
                    💡 **Gợi ý 3 chủ đề đăng bài kích thích tương tác cực mạnh cho nhóm sinh viên:**
                    
                    1. ⚖️ **Cân bằng giữa Học tập & Đi làm thêm (Internship / Part-time):**
                       - *Câu mở đầu gợi ý:* "Vừa gánh deadline môn học, vừa chạy việc ở công ty — Các bạn trong nhóm đang quản lý thời gian như thế nào để không bị kiệt sức (burnout)?"
                       - *Điểm nhấn:* Kích thích mọi người bình luận chia sẻ thời gian biểu thực tế.
                    
                    2. 🛠️ **Top công cụ & Extension AI không thể thiếu kỳ này:**
                       - *Câu mở đầu gợi ý:* "Nếu chỉ được chọn 3 công cụ công nghệ giúp việc học của bạn 'dễ thở' hơn gấp đôi, bạn sẽ chọn gì? Cùng chia sẻ kho bí kíp bên dưới nhé!"
                       - *Điểm nhấn:* Thu hút chia sẻ kiến thức, lưu bài viết (Save post).
                    
                    3. 🎯 **Tranh luận: Nên học sâu chuyên môn (Specialist) hay học đa năng (Generalist)?**
                       - *Câu mở đầu gợi ý:* "Năm 3, năm 4 nên đào sâu một ngôn ngữ/framework duy nhất hay nên biết cả Frontend, Backend, DevOps? Góc nhìn thực tế từ các tiền bối đi trước!"
                       - *Điểm nhấn:* Tạo luồng tranh luận học thuật sôi nổi và đa chiều.
                    
                    🔥 *Mẹo tăng tương tác: Đính kèm một bức ảnh thực tế hoặc tạo cuộc thăm dò ý kiến (Poll) để tương tác bùng nổ nhé!*
                    """;
        }

        // 6. Công nghệ & Lập trình (Spring Boot, React, Kafka, Docker, Database, Cloud, Architecture)
        if (containsAny(normalized, "spring boot", "java", "react", "kafka", "docker", "microservice", "rest api", "sql", "postgresql", "frontend", "backend", "webrtc", "websocket", "redis", "jwt", "oauth")) {
            return handleTechQuestion(normalized, message);
        }

        // 7. Phương pháp học tập & Thi cử
        if (containsAny(normalized, "on thi", "hoc tap", "phuong phap hoc", "pomodoro", "bi quyet hoc", "tap trung", "qua mon", "gpa", "hoc nhanh")) {
            return """
                    📚 **3 Chiến lược học tập thông minh giúp tăng 50% hiệu suất học & đạt GPA cao:**
                    
                    - ⏱️ **Kỹ thuật Pomodoro cải tiến:**
                      - 25 phút tập trung tuyệt đối (tắt mọi thông báo điện thoại) + 5 phút giải lao nhẹ nhàng (uống nước, vươn vai). Sau 4 chu kỳ, nghỉ dài 20 phút.
                    
                    - 🧠 **Active Recall (Chủ động gợi nhớ) kết hợp Spaced Repetition (Lặp lại ngắt quãng):**
                      - Sau khi đọc tài liệu, hãy đóng sách lại và viết ra giấy mọi thứ bạn vừa nhớ. Ôn lại sau 1 ngày, 3 ngày, 7 ngày và 14 ngày để biến trí nhớ ngắn hạn thành dài hạn.
                    
                    - 🗣️ **Kỹ thuật Feynman (Giảng giải cho người khác):**
                      - Hãy thử giải thích một khái niệm phức tạp bằng ngôn từ đơn giản nhất cho một người bạn chưa biết gì. Chỗ nào bạn ngập ngừng chính là lỗ hổng kiến thức cần đọc lại ngay!
                    
                    💪 *Bắt đầu ngay hôm nay với mục tiêu rõ ràng, bạn chắc chắn sẽ gặt hái kết quả xuất sắc!*
                    """;
        }

        // 8. Tâm sự, Động viên, Giải tỏa căng thẳng (Stress, Deadline, Mệt mỏi)
        if (containsAny(normalized, "met", "stress", "ap luc", "nan", "deadline", "buon", "cang thang", "chan", "lo lang")) {
            return """
                    🌿 **Gửi đến bạn một chút bình yên và năng lượng tích cực hôm nay:**
                    
                    Tôi hiểu rằng giai đoạn này có rất nhiều deadline, bài vở và áp lực đang đè nặng lên vai bạn. Nhưng hãy nhớ rằng:
                    - ☕ **Bạn đã rất nỗ lực:** Dành cho bản thân 15-30 phút nghỉ ngơi thật sự, uống một tách trà ấm hoặc nghe một bản nhạc nhẹ nhàng.
                    - 🎯 **Chia nhỏ vấn đề:** Thay vì nhìn cả một ngọn núi việc, hãy chọn ra đúng **1 việc nhỏ nhất** và hoàn thành nó trước.
                    - 🌈 **Mọi thử thách đều sẽ qua:** Cảm giác mệt mỏi này chỉ là tạm thời, những kiến thức và sự kiên trì bạn tích lũy hôm nay sẽ là hành trang vô giá cho tương lai.
                    
                    Hít thở một hơi thật sâu nào! Nếu bạn cần tôi hỗ trợ tóm tắt tài liệu, viết dàn ý bài hay đơn giản là trò chuyện giải tỏa, tôi luôn ở đây đồng hành cùng bạn nhé! 💙
                    """;
        }

        // 9. Lời cảm ơn
        if (containsAny(normalized, "cam on", "thank", "tuyet voi", "hay qua", "gioi qua", "ok cam on")) {
            return """
                    Rất vui vì đã giúp ích được cho bạn! 😊✨
                    
                    Chúc bạn học tập hiệu quả, làm việc nhiều cảm hứng và có những trải nghiệm thật tuyệt vời trên KLTN Social. Bất cứ khi nào cần hỗ trợ, cứ nhắn cho tôi nhé! 🚀
                    """;
        }

        // 10. Phản hồi thông minh, chuyên sâu và cá nhân hóa cho câu hỏi tự do
        return generateContextualAssistance(message, normalized);
    }

    private String generateCaptionsForChat(String normalized) {
        if (containsAny(normalized, "cong nghe", "lap trinh", "it", "code")) {
            return """
                    ✨ **Gợi ý 3 caption bài viết chủ đề Công nghệ & Lập trình:**
                    
                    1. 💻 *Mỗi dòng code hôm nay là một bước tiến gần hơn đến sản phẩm hoàn hảo. Kiên trì debug, thành quả sẽ tới!* 🚀 #LapTrinh #DevLife
                    2. ⚡ *Cà phê đầy cốc, bàn phím sẵn sàng — cùng chinh phục những tính năng mới trong dự án hôm nay!* ☕ #TechLife #CodingEveryday
                    3. 🌐 *Không ngừng học hỏi, cập nhật công nghệ mới để kiến tạo những giá trị hữu ích cho cộng đồng.* 💡 #CongNgheThongTin #SinhVienIT
                    """;
        }

        if (containsAny(normalized, "hoc tap", "nang luong", "ngay moi", "tich cuc")) {
            return """
                    🌟 **Gợi ý 3 caption bài viết tràn đầy năng lượng học tập:**
                    
                    1. ☀️ *Nạp đầy 100% năng lượng cho một ngày học tập bứt phá! Mỗi nỗ lực nhỏ hôm nay đều là viên gạch xây dựng tương lai.* 💪 #HocTapMoiNgay #NangLuongTichCuc
                    2. ☕ *Bắt đầu ngày mới với nụ cười rạng rỡ và quyết tâm hoàn thành trọn vẹn mọi mục tiêu đề ra.* ✨ #SinhVienNangDong #KhoaLuanTotNghiep
                    3. 📚 *Học tập không chỉ là tích lũy kiến thức, mà còn là hành trình khám phá và phát triển bản thân mỗi ngày.* 🌿 #TuoiTreDamMe #ThanhXuanDaiHoc
                    """;
        }

        return """
                📝 **Gợi ý 3 caption bài viết ấn tượng dành cho bạn:**
                
                1. 🌿 *Lưu giữ những khoảnh khắc ý nghĩa và năng lượng tích cực của ngày hôm nay cùng mọi người.* ✨ #ChiaSeKhoanhKhac #KLTNSocial
                2. 🎯 *Mỗi ngày trôi qua là một cơ hội để học thêm điều mới và hoàn thiện bản thân hơn hôm qua.* 🚀 #PhatTrienBanThan #NhipSongTre
                3. 💫 *Hành trình vạn dặm bắt đầu từ một bước chân. Cùng cố gắng vì những mục tiêu lớn phía trước nhé!* 🌈 #KetNoiDamMe #SinhVienVietNam
                """;
    }

    private String generateHashtagsForChat(String normalized) {
        if (containsAny(normalized, "cong nghe", "it", "lap trinh", "sinh vien")) {
            return """
                    🏷️ **Danh sách hashtag thịnh hành về Công nghệ thông tin & Đời sống sinh viên:**
                    
                    🔹 **Chủ đề Công nghệ & Lập trình:**
                    `#CongNgheThongTin` `#SinhVienIT` `#LapTrinhMoiNgay` `#DevLife` `#KhoaLuanTotNghiep` `#CodingCommunity` `#TechGenZ`
                    
                    🔹 **Chủ đề Đời sống Sinh viên & Học tập:**
                    `#DoiSongSinhVien` `#HocTapTichCuc` `#DaiHoc` `#KLTNSocial` `#ThanhXuanSinhVien` `#DeadlineMoiNgay` `#NangLuongMoiNgay`
                    
                    💡 *Mẹo: Sử dụng từ 4 - 6 hashtag phù hợp nhất để bài viết tiếp cận đúng bạn bè có cùng quan tâm nhé!*
                    """;
        }

        return """
                🏷️ **Bộ hashtag phổ biến và viral trên mạng xã hội:**
                
                `#KLTNSocial` `#ChiaSeYTuong` `#KetNoiBanBe` `#HocTapMoiNgay` `#KhoaLuanTotNghiep` `#TuoiTreCongNghe` `#NangLuongTichCuc` `#SinhVienVietNam`
                """;
    }

    private String handleTechQuestion(String normalized, String originalMessage) {
        if (containsAny(normalized, "spring boot", "microservice")) {
            return """
                    🛠️ **Về Spring Boot & Kiến trúc Microservices:**
                    
                    - **Spring Boot 3 / Java 21:** Tối ưu hóa hiệu năng với Virtual Threads, GraalVM native image và Spring Cloud 2025+.
                    - **Service Discovery & API Gateway:** Sử dụng Netflix Eureka kết hợp Spring Cloud Gateway để định tuyến tập trung, quản lý JWT Authentication và CORS.
                    - **Giao tiếp liên dịch vụ:**
                      - *Đồng bộ (Synchronous):* Sử dụng **OpenFeign Client** cho các truy vấn dữ liệu nhanh giữa các service.
                      - *Bất đồng bộ (Asynchronous):* Sử dụng **Apache Kafka** để bắn event (như thông báo, gửi mail, kiểm duyệt AI) giúp hệ thống mở rộng linh hoạt (decoupled).
                    
                    Bạn đang cần hỗ trợ chi tiết về cấu hình hay logic xử lý nào của Spring Boot?
                    """;
        }

        if (containsAny(normalized, "react", "frontend", "tailwind")) {
            return """
                    ⚛️ **Về Phát triển Frontend hiện đại với React & TypeScript:**
                    
                    - **Component-Driven:** Chia nhỏ UI thành các component đơn trách nhiệm, tái sử dụng cao.
                    - **Custom Hooks:** Tách biệt logic gọi API, state management và business logic ra khỏi phần giao diện render.
                    - **Realtime UI:** Kết hợp WebSocket / STOMP để cập nhật realtime tin nhắn chat, thông báo và lượt tương tác bài viết tức thì.
                    
                    Bạn cần viết thêm component hay tối ưu performance phần nào?
                    """;
        }

        return String.format("""
                💻 **Giải đáp kỹ thuật:**
                
                Về câu hỏi của bạn: *"%s"*
                
                Trong kiến trúc hệ thống hiện đại, việc phân tách rõ ràng giữa Controller (Presentation Layer), Service (Application Layer), Repository (Domain/Infrastructure) và áp dụng DTO / API Response chuẩn mực sẽ giúp ứng dụng dễ bảo trì và mở rộng nhất.
                
                Nếu bạn cần mẫu code cụ thể, hãy cho tôi biết chi tiết yêu cầu nhé!
                """, originalMessage);
    }

    /**
     * Gợi ý danh sách 3 caption theo topic và tone
     */
    public List<String> suggestCaptions(String topic, String tone) {
        String cleanTopic = topic != null && !topic.isBlank() ? topic.trim() : "một ngày học tập";
        String normalized = normalizer.normalize(cleanTopic.toLowerCase());

        if (containsAny(normalized, "hoc tap", "nang luong", "ngay moi")) {
            return List.of(
                    "Nạp đầy 100% năng lượng cho một ngày học tập và bứt phá giới hạn! Mỗi nỗ lực hôm nay là nền tảng vững chắc cho tương lai. 🌟",
                    "Bắt đầu ngày mới với nụ cười rạng rỡ và quyết tâm hoàn thành xuất sắc mọi mục tiêu đề ra! ☕✨",
                    "Học tập không chỉ là tích lũy kiến thức, mà còn là hành trình khám phá và hoàn thiện bản thân mỗi ngày. 📚💪"
            );
        }

        if (containsAny(normalized, "cong nghe", "lap trinh", "code", "it")) {
            return List.of(
                    "Mỗi dòng code hôm nay là một mảnh ghép kiến thức, kiên trì debug sẽ tạo nên thành quả hoàn hảo! 💻🚀",
                    "Đam mê công nghệ, không ngừng học hỏi và sáng tạo để kiến tạo tương lai số. ⚡🌐",
                    "Cà phê đầy cốc, bàn phím sẵn sàng — cùng nhau chinh phục dự án khóa luận hôm nay! ☕🔥"
            );
        }

        if (containsAny(normalized, "khoa luan", "do an", "tot nghiep")) {
            return List.of(
                    "Những ngày tháng dồn hết tâm huyết cho đồ án tốt nghiệp — chặng đường đáng nhớ nhất của thời sinh viên! 🎓✨",
                    "Kiên trì từng bước hoàn thiện khóa luận, chuẩn bị sẵn sàng cho ngày bảo vệ rực rỡ phía trước. 🚀📘",
                    "Cảm ơn những người bạn đồng hành tuyệt vời đã cùng nhau vượt qua bao đêm chạy deadline đồ án! 💖💪"
            );
        }

        return List.of(
                cleanTopic + " - Chia sẻ những khoảnh khắc ý nghĩa và tích cực của ngày hôm nay cùng mọi người. 🌿✨",
                "Khám phá và lưu giữ những trải nghiệm thú vị xoay quanh " + cleanTopic + ". 🌟🚀",
                "Mỗi trải nghiệm hôm nay là một bài học quý giá cho tương lai. 💫💡"
        );
    }

    /**
     * Gợi ý danh sách hashtag
     */
    public List<String> suggestHashtags(String content, int count) {
        int max = count > 0 ? Math.min(count, 12) : 5;
        String normalized = normalizer.normalize(content != null ? content.toLowerCase() : "");

        Set<String> tags = new LinkedHashSet<>();

        if (containsAny(normalized, "cong nghe", "it", "lap trinh", "code", "software", "developer")) {
            tags.addAll(List.of("#CongNgheThongTin", "#LapTrinh", "#SinhVienIT", "#DevLife", "#CodingMoiNgay", "#TechGenZ"));
        }

        if (containsAny(normalized, "khoa luan", "do an", "tot nghiep", "kltn")) {
            tags.addAll(List.of("#KhoaLuanTotNghiep", "#BaoVeDoAn", "#KLTN", "#SinhVienNamCuoi", "#KySuTuongLai"));
        }

        if (containsAny(normalized, "hoc tap", "sinh vien", "truong", "lop", "deadline")) {
            tags.addAll(List.of("#SinhVien", "#HocTapMoiNgay", "#DoiSongSinhVien", "#DaiHoc", "#NangLuongTichCuc"));
        }

        // Tags mặc định
        tags.addAll(List.of("#KLTNSocial", "#ChiaSe", "#KetNoi", "#TuoiTre", "#CuocSong"));

        return tags.stream().limit(max).collect(Collectors.toList());
    }

    /**
     * Nâng cấp, làm đẹp văn bản
     */
    public String enhanceText(String content, String style) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String enhanced = content.trim();

        // Chuẩn hóa teencode cơ bản
        enhanced = enhanced.replaceAll("(?i)\\bko\\b", "không")
                .replaceAll("(?i)\\bdc\\b", "được")
                .replaceAll("(?i)\\bvs\\b", "với")
                .replaceAll("(?i)\\bmk\\b", "mình")
                .replaceAll("(?i)\\bb\\b", "bạn")
                .replaceAll("(?i)\\bbt\\b", "biết")
                .replaceAll("(?i)\\bj\\b", "gì")
                .replaceAll("(?i)\\bntn\\b", "như thế nào")
                .replaceAll("(?i)\\bh\\b", "giờ")
                .replaceAll("(?i)\\bok\\b", "OK");

        // Viết hoa chữ cái đầu tiên
        if (!enhanced.isEmpty()) {
            enhanced = Character.toUpperCase(enhanced.charAt(0)) + (enhanced.length() > 1 ? enhanced.substring(1) : "");
        }

        // Đảm bảo kết thúc có dấu câu nếu chưa có
        if (!enhanced.endsWith(".") && !enhanced.endsWith("!") && !enhanced.endsWith("?")) {
            if ("hào hứng, sôi nổi".equalsIgnoreCase(style)) {
                enhanced += " ✨🚀";
            } else {
                enhanced += ".";
            }
        }

        return enhanced;
    }

    /**
     * Tóm tắt bài viết
     */
    public String summarizeText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String[] sentences = content.split("(?<=[.!?\\n])\\s+");
        if (sentences.length <= 2) {
            return content.trim();
        }

        // Lấy 2 câu quan trọng đầu tiên
        return (sentences[0].trim() + " " + sentences[1].trim()).trim();
    }

    /**
     * Gợi ý câu trả lời nhanh
     */
    public List<String> suggestReplies(String postContent, String commentContent) {
        String normalized = normalizer.normalize((postContent + " " + (commentContent != null ? commentContent : "")).toLowerCase());

        if (containsAny(normalized, "cam on", "thank", "tuyet voi", "hay qua", "dep")) {
            return List.of(
                    "Cảm ơn bạn nhiều nhé! Chúc bạn một ngày tràn đầy niềm vui và năng lượng ❤️",
                    "Rất vui vì bài viết mang lại điều hữu ích cho bạn! 🎉",
                    "Cảm ơn bạn đã luôn đồng hành và ủng hộ nhé! 👏✨"
            );
        }

        if (containsAny(normalized, "hoi", "sao", "the nao", "o dau", "giup", "?")) {
            return List.of(
                    "Mình đã gửi thêm thông tin chi tiết, bạn kiểm tra nhé! 💡",
                    "Ý kiến của bạn rất hay! Chúng ta cùng trao đổi thêm nhé. 🤝",
                    "Cảm ơn câu hỏi rất thú vị của bạn! 🌟"
            );
        }

        return List.of(
                "Bài viết rất hay và ý nghĩa! Cảm ơn bạn đã chia sẻ nhé 👏",
                "Tuyệt vời quá! Chúc bạn luôn tràn đầy năng lượng tích cực 🎉",
                "Đồng quan điểm với bạn! Cùng nhau cố gắng nhé ❤️✨"
        );
    }

    /**
     * Gợi ý tiểu sử cá nhân (Profile Bio)
     */
    public List<String> suggestBios(String name, String major, String interests, String tone) {
        String safeName = (name != null && !name.isBlank()) ? name.trim() : "Tôi";
        String safeMajor = (major != null && !major.isBlank()) ? major.trim() : "Sinh viên";
        String safeInterests = (interests != null && !interests.isBlank()) ? interests.trim() : "Công nghệ & Cuộc sống";
        String safeTone = (tone != null) ? tone.toLowerCase() : "năng động, trẻ trung";

        if (safeTone.contains("chuyên nghiệp") || safeTone.contains("nghiêm túc")) {
            return List.of(
                    String.format("💼 %s | Đam mê %s. Luôn hướng tới sự chuẩn mực, sáng tạo và không ngừng nâng cao bản thân. 🌐", safeMajor, safeInterests),
                    String.format("🎯 Mục tiêu rõ ràng, tư duy cầu tiến | %s | Yêu thích nghiên cứu & ứng dụng %s 🚀", safeMajor, safeInterests),
                    String.format("✨ Kết nối để cùng chia sẻ cơ hội và kiến thức về %s. Hân hạnh được làm quen! 🤝", safeInterests)
            );
        }

        if (safeTone.contains("tối giản") || safeTone.contains("sâu lắng")) {
            return List.of(
                    String.format("🌱 %s | Yêu những điều giản dị và đam mê %s.", safeMajor, safeInterests),
                    String.format("☕ Một chút tĩnh lặng, một chút đam mê cùng %s. Sống trọn từng khoảnh khắc ✨", safeInterests),
                    String.format("📖 Học tập, trải nghiệm và sẻ chia | %s | Keep it simple & true 🌿", safeMajor)
            );
        }

        // Mặc định: Năng động, hài hước, trẻ trung
        return List.of(
                String.format("🚀 %s năng lượng tràn đầy! Đam mê %s và mê kết nối bạn bè 🌟", safeMajor, safeInterests),
                String.format("✨ Sống hết mình với đam mê %s | Học hết sức, vui hết mình! Cùng kết nối nhé ❤️", safeInterests),
                String.format("💻 %s chính hiệu | Thích %s, mê cà phê và những ý tưởng mới mẻ ☕🔥", safeMajor, safeInterests)
        );
    }

    /**
     * Phân tích cảm xúc, mức độ lan tỏa và gợi ý tối ưu bài viết
     */
    public PostAnalysisLocalResult analyzePost(String content) {
        if (content == null || content.isBlank()) {
            return PostAnalysisLocalResult.builder()
                    .sentiment("NEUTRAL")
                    .engagementScore(50)
                    .vibe("Chưa có nội dung")
                    .suggestions(List.of("Hãy nhập nội dung bài viết để AI có thể đánh giá chi tiết."))
                    .build();
        }

        String normalized = normalizer.normalize(content.toLowerCase());
        int length = content.length();

        // 1. Phân tích Sentiment
        boolean isPositive = containsAny(normalized, "vui", "tuyet", "thanh cong", "cam on", "yeu", "hanh phuc", "kham pha", "hao hung", "chuc mung");
        boolean isNegative = containsAny(normalized, "buon", "that vong", "chan", "kho", "stress", "met", "te", "khoc");

        String sentiment = isPositive && !isNegative ? "POSITIVE" : (isNegative && !isPositive ? "NEGATIVE" : "NEUTRAL");

        // 2. Tính điểm tương tác dự kiến (50 - 95)
        int score = 65;
        if (length >= 50 && length <= 400) score += 10;
        if (content.contains("?") || content.contains("ai") || content.contains("sao")) score += 8;
        if (content.matches(".*[\\p{So}\\p{Cs}].*") || content.contains("❤️") || content.contains("✨") || content.contains("🔥")) score += 7;
        if (content.contains("#")) score += 5;
        score = Math.min(score, 95);

        // 3. Nhận diện vibe
        String vibe;
        if (isPositive) {
            vibe = "Tích cực & Truyền cảm hứng ✨";
        } else if (containsAny(normalized, "hoc", "chia se", "kinh nghiem", "code", "lap trinh", "cong nghe")) {
            vibe = "Học thuật & Chia sẻ kiến thức 📚";
        } else if (containsAny(normalized, "hoi", "cac ban", "ai biet", "?")) {
            vibe = "Thảo luận & Giao lưu sôi nổi 💬";
        } else {
            vibe = "Tâm sự & Kết nối thân thiện 🌿";
        }

        // 4. Gợi ý cải thiện
        List<String> suggestions = new ArrayList<>();
        if (!content.contains("?")) {
            suggestions.add("Thêm một câu hỏi mở ở cuối bài (ví dụ: 'Các bạn thấy sao?', 'Cùng chia sẻ nhé!') để tăng lượt bình luận.");
        }
        if (!content.contains("#")) {
            suggestions.add("Gắn thêm 2-3 hashtag thịnh hành (như #KLTN, #SinhVien) để bài viết tiếp cận nhiều độc giả hơn.");
        }
        if (length < 30) {
            suggestions.add("Mở rộng thêm 1-2 câu chia sẻ cảm nghĩ cụ thể để bài viết có chiều sâu hơn.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Bài viết của bạn đã rất cân đối, thu hút và chuẩn mực! Sẵn sàng đăng tải.");
        }

        return PostAnalysisLocalResult.builder()
                .sentiment(sentiment)
                .engagementScore(score)
                .vibe(vibe)
                .suggestions(suggestions)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PostAnalysisLocalResult {
        private String sentiment;
        private int engagementScore;
        private String vibe;
        private List<String> suggestions;
    }

    private String getDynamicGreeting(String lower) {
        LocalTime now = LocalTime.now();
        String timeGreeting;
        if (now.getHour() < 12) {
            timeGreeting = "Chào buổi sáng tốt lành! ☀️";
        } else if (now.getHour() < 18) {
            timeGreeting = "Chào buổi chiều tràn đầy năng lượng! 🌤️";
        } else {
            timeGreeting = "Chào buổi tối ấm áp! 🌙";
        }

        String[] greetings = new String[]{
                timeGreeting + " Tôi là **Trợ lý AI KLTN Social**. Hôm nay tôi có thể hỗ trợ gì cho bạn trong việc học tập, lập trình hay sáng tạo bài viết?",
                "Xin chào bạn! ✨ Rất vui được đồng hành cùng bạn hôm nay. Bạn đang cần tìm ý tưởng viết bài, gợi ý caption hay hỗ trợ đồ án?",
                "Hello bạn! 👋 Chúc bạn một ngày học tập và làm việc thật hiệu quả. Đang có câu hỏi kỹ thuật hay cần tóm tắt nội dung gì cứ thoải mái nhắn cho tôi nhé!",
                timeGreeting + " Trợ lý AI KLTN Social luôn sẵn sàng giải đáp và cùng bạn chia sẻ mọi ý tưởng sáng tạo!"
        };

        return greetings[Math.abs(lower.hashCode()) % greetings.length];
    }

    private String generateContextualAssistance(String message, String normalized) {
        // Database & SQL
        if (containsAny(normalized, "sql", "database", "co so du lieu", "index", "khoa chinh", "query", "postgresql", "mysql")) {
            return """
                    🗄️ **Tối ưu Cơ sở dữ liệu & Truy vấn SQL:**
                    
                    1. **Đánh Index hợp lý:**
                       - Tạo Index trên các cột thường xuyên xuất hiện trong mệnh đề `WHERE`, `JOIN` và `ORDER BY`.
                       - Tránh đánh Index tràn lan trên các bảng có tần suất ghi (`INSERT`/`UPDATE`) rất cao.
                    2. **Tránh N+1 Query:**
                       - Trong JPA/Hibernate, sử dụng `JOIN FETCH` hoặc `@EntityGraph` để nạp dữ liệu quan hệ trong 1 truy vấn duy nhất.
                    3. **Phân trang hiệu quả:**
                       - Dùng Keyset Pagination (Seek method) thay cho `OFFSET/LIMIT` lớn khi dữ liệu lên tới hàng trăm nghìn bản ghi.
                    
                    Bạn đang cần tối ưu câu truy vấn cụ thể nào? Hãy gửi để tôi phân tích nhé!
                    """;
        }

        // Security & Authentication
        if (containsAny(normalized, "cookie", "token", "jwt", "bao mat", "security", "xss", "csrf", "localstorage", "auth")) {
            return """
                    🔒 **Kiến trúc Bảo mật & Quản lý Token chuẩn sản xuất:**
                    
                    - **Access Token:** Có thời hạn ngắn (ví dụ: 15 phút), lưu trong memory (State/Context) của ứng dụng Frontend để chống XSS.
                    - **Refresh Token:** Lưu trữ an toàn trong `HttpOnly; Secure; SameSite=Strict` Cookie, ngăn chặn hoàn toàn việc đánh cắp token qua JavaScript/LocalStorage.
                    - **Silent Refresh:** Khi Access Token hết hạn, tự động gọi API `/auth/refresh` bằng Cookie để cấp mới Access Token mà người dùng không bị gián đoạn trải nghiệm.
                    
                    Bạn đang muốn tìm hiểu sâu hơn về luồng xác thực nào?
                    """;
        }

        // Học tập & Nghiên cứu khoa học
        if (containsAny(normalized, "nghien cuu", "tai lieu", "viet bao", "khoa luan", "de tai", "thao luan")) {
            return """
                    📚 **Phương pháp Nghiên cứu & Hoàn thiện Đề tài:**
                    
                    - **Xác định bài toán cốt lõi:** Làm rõ khoảng trống nghiên cứu (Research Gap) và tính cấp thiết của đề tài.
                    - **Khảo sát tài liệu:** Tìm kiếm các bài báo trên IEEE Xplore, ScienceDirect, Google Scholar trong 3-5 năm gần nhất.
                    - **Minh chứng thực nghiệm:** Chuẩn bị bộ dữ liệu thử nghiệm rõ ràng, so sánh hiệu năng trước và sau khi áp dụng giải pháp.
                    
                    Cứ đặt câu hỏi chi tiết về đề tài của bạn, tôi sẽ cùng bạn phân tích nhé!
                    """;
        }

        // Câu hỏi mở chung
        return String.format("""
                💡 **Về vấn đề bạn đang quan tâm:**
                
                *"%s"*
                
                Để giải quyết bài toán này một cách tối ưu và nhanh chóng nhất:
                - 🎯 **Trọng tâm:** Xác định rõ mục tiêu đầu ra và các ràng buộc cụ thể của hệ thống/yêu cầu.
                - 🚀 **Các bước tiếp cận:** Bắt đầu từ nguyên mẫu đơn giản nhất (MVP), kiểm thử tính khả thi rồi mở rộng từng bước.
                - 🛠️ **Công cụ hỗ trợ:** Bạn có thể kết hợp các tính năng AI của KLTN Social (gợi ý caption, phân tích bài viết, tóm tắt tin nhắn) để tăng tốc độ xử lý.
                
                Hãy chia sẻ thêm chi tiết nếu bạn muốn tôi đi sâu vào khía cạnh cụ thể nhé! ✨
                """, message);
    }

    /**
     * Tóm tắt cuộc trò chuyện (tin nhắn chưa đọc kèm hình ảnh) - Fallback thông minh
     */
    public MessageSummaryDto.SummarizeMessagesResponse summarizeMessages(
            String conversationName,
            Boolean isGroup,
            List<MessageSummaryDto.MessageItem> messages) {

        if (messages == null || messages.isEmpty()) {
            return MessageSummaryDto.SummarizeMessagesResponse.builder()
                    .summary("Hiện tại không có tin nhắn mới nào cần tóm tắt.")
                    .mediaDescription("Không có tệp đính kèm nào.")
                    .actionItems(Collections.emptyList())
                    .messageCount(0)
                    .imageCount(0)
                    .build();
        }

        int totalMessages = messages.size();
        int imageCount = 0;
        List<String> imageHints = new ArrayList<>();
        List<String> actionItems = new ArrayList<>();
        Set<String> participants = new LinkedHashSet<>();

        for (MessageSummaryDto.MessageItem item : messages) {
            if (item.getSenderName() != null && !item.getSenderName().isBlank()) {
                participants.add(item.getSenderName());
            }

            String text = item.getText() != null ? item.getText() : "";
            String media = item.getMediaUrl() != null ? item.getMediaUrl() : "";

            boolean isImage = (media.contains(".jpg") || media.contains(".jpeg") || media.contains(".png") || media.contains(".webp") || media.contains("images"))
                    || (text.startsWith("http") && (text.contains(".jpg") || text.contains(".jpeg") || text.contains(".png") || text.contains(".webp")));

            if (isImage) {
                imageCount++;
                imageHints.add(String.format("Ảnh do %s gửi lúc %s",
                        item.getSenderName() != null ? item.getSenderName() : "thành viên",
                        item.getTime() != null ? item.getTime() : "vừa xong"));
            }

            // Tìm kiếm các việc cần làm, lịch hẹn, deadline trong tin nhắn
            String lower = text.toLowerCase();
            String normal = normalizer.normalize(lower);
            if (containsAny(normal, "deadline", "han chot", "nop bai", "hop", "gap nhau", "gui file", "sua lai", "check giup", "xem giup", "kiem tra", "demo", "thuyet trinh")) {
                String senderPrefix = item.getSenderName() != null ? item.getSenderName() + ": " : "";
                actionItems.add(senderPrefix + text);
            }
        }

        if (actionItems.size() > 5) {
            actionItems = actionItems.subList(0, 5);
        }

        String convTitle = (conversationName != null && !conversationName.isBlank()) ? conversationName : (Boolean.TRUE.equals(isGroup) ? "Nhóm chat" : "Cuộc trò chuyện");
        String membersStr = participants.isEmpty() ? "các thành viên" : String.join(", ", participants);

        String summaryText = String.format(
                "Trong %d tin nhắn gần nhất của cuộc trò chuyện '%s' giữa %s: Các thành viên đã trao đổi về tiến độ công việc, chia sẻ thông tin và cập nhật tài liệu quan trọng.",
                totalMessages, convTitle, membersStr
        );

        String mediaDesc = imageCount > 0
                ? String.format("Cuộc trò chuyện có %d hình ảnh đính kèm (%s).", imageCount, String.join("; ", imageHints.stream().limit(3).toList()))
                : "Không có hình ảnh đính kèm trong loạt tin nhắn này.";

        if (actionItems.isEmpty()) {
            actionItems.add("Tiếp tục theo dõi cuộc trò chuyện để cập nhật thêm thông tin.");
        }

        return MessageSummaryDto.SummarizeMessagesResponse.builder()
                .summary(summaryText)
                .mediaDescription(mediaDesc)
                .actionItems(actionItems)
                .messageCount(totalMessages)
                .imageCount(imageCount)
                .build();
    }

    private boolean containsAny(String source, String... targets) {
        if (source == null || source.isBlank()) return false;
        for (String target : targets) {
            if (source.contains(target)) {
                return true;
            }
        }
        return false;
    }
}
