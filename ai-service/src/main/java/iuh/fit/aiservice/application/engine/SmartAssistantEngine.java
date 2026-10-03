package iuh.fit.aiservice.application.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        if (message == null || message.isBlank()) {
            return "Chào bạn! Tôi là **Trợ lý AI KLTN Social**. Bạn cần tôi hỗ trợ viết caption, gợi ý hashtag hay giải đáp câu hỏi nào không?";
        }

        String lower = message.toLowerCase().trim();
        String normalized = normalizer.normalize(lower);

        // 1. Gợi ý Caption bài viết
        if (containsAny(normalized, "caption", "viet stt", "stt", "dang bai", "status") || 
            (containsAny(normalized, "goi y", "viet cho toi", "tao cho toi") && containsAny(normalized, "nang luong", "hoc tap", "ngay moi"))) {
            return generateCaptionsForChat(normalized);
        }

        // 2. Gợi ý Hashtag
        if (containsAny(normalized, "hashtag", "hash tag", "the tag", "tag pho bien", "tag thinh hanh")) {
            return generateHashtagsForChat(normalized);
        }

        // 3. Bảo vệ đồ án / Khóa luận tốt nghiệp
        if (containsAny(normalized, "bao ve do an", "bao ve khoa luan", "do an tot nghiep", "khoa luan tot nghiep", "bao ve kltn", "do an")) {
            return """
                    🎓 **Tóm tắt 5 bước bảo vệ Đồ án / Khóa luận tốt nghiệp thành công:**
                    
                    1. **Hoàn thiện Báo cáo & Tài liệu chuẩn chỉ:**
                       - Kiểm tra kỹ lỗi chính tả, định dạng căn lề, mục lục, danh mục hình vẽ và tài liệu tham khảo theo quy chuẩn khoa/trường.
                    
                    2. **Thiết kế Slide thuyết trình ấn tượng:**
                       - Giới hạn 15 - 20 slide súc tích. Tập trung vào: *Vấn đề nghiên cứu -> Giải pháp kiến trúc -> Kết quả đạt được -> Demo sản phẩm*.
                    
                    3. **Luyện tập thuyết trình (Timing 10-15 phút):**
                       - Nói to, rõ ràng, phong thái tự tin. Phân chia thời gian hợp lý giữa phần trình bày lý thuyết và phần demo trực tiếp.
                    
                    4. **Chuẩn bị kịch bản Demo & Kịch bản dự phòng (Backup):**
                       - Quay sẵn 1 video demo ngắn phòng trường hợp mạng hoặc server gặp sự cố trong buổi hội đồng.
                    
                    5. **Chuẩn bị tâm thế trả lời phản biện:**
                       - Lắng nghe trọn vẹn câu hỏi của thầy cô, ghi chép lại, trả lời trọng tâm, trung thực và cầu thị.
                    
                    ✨ *Chúc bạn có một buổi bảo vệ khóa luận thành công rực rỡ và đạt điểm số tối đa!*
                    """;
        }

        // 4. Chủ đề thảo luận / Ý tưởng đăng bài cho nhóm sinh viên
        if (containsAny(normalized, "chu de thao luan", "y tuong dang bai", "dang len nhom", "post group", "nhom sinh vien")) {
            return """
                    💡 **Gợi ý 3 chủ đề thảo luận sôi nổi cho nhóm sinh viên:**
                    
                    1. **Chủ đề 1: Bí quyết cân bằng giữa việc Học và Đi làm thêm (Part-time / Internship)**
                       - *Gợi ý mở đầu:* "Vừa chạy deadline môn học, vừa làm thêm kiếm kinh nghiệm — Các bạn trong nhóm đang quản lý thời gian như thế nào để không bị 'burnout'?"
                    
                    2. **Chủ đề 2: Bộ công cụ (Tools & AI) đắc lực hỗ trợ sinh viên học tập hiệu quả**
                       - *Gợi ý mở đầu:* "Top 3 ứng dụng/công cụ công nghệ mà bạn thấy không thể thiếu trong kỳ học này là gì? Cùng chia sẻ để mọi người cùng tối ưu hóa việc học nhé!"
                    
                    3. **Chủ đề 3: Định hướng nghề nghiệp — Frontend, Backend hay DevOps / AI?**
                       - *Gợi ý mở đầu:* "Năm 3, năm 4 nên tập trung sâu vào một mảng hay học rộng đa kỹ năng (Fullstack)? Góc nhìn và trải nghiệm thực tế từ các anh chị đi trước!"
                    
                    🔥 *Mẹo nhỏ: Đính kèm một bức ảnh sinh động hoặc tạo cuộc bình chọn (Poll) để tăng tương tác gấp 3 lần nhé!*
                    """;
        }

        // 5. Câu hỏi về Công nghệ / Lập trình (Java, Spring Boot, React, Kafka, Docker, AI, Microservices)
        if (containsAny(normalized, "spring boot", "java", "react", "kafka", "docker", "microservice", "rest api", "sql", "postgresql", "frontend", "backend")) {
            return handleTechQuestion(normalized, message);
        }

        // 6. Câu hỏi về Phương pháp học tập / Ôn thi
        if (containsAny(normalized, "on thi", "hoc tap", "phuong phap hoc", "pomodoro", "bi quyet hoc", "tap trung")) {
            return """
                    📚 **3 Phương pháp học tập & ôn thi hiệu quả cao dành cho bạn:**
                    
                    - ⏱️ **Kỹ thuật Pomodoro:** Học tập trung cao độ trong 25 phút, nghỉ ngắn 5 phút. Lặp lại 4 chu kỳ thì nghỉ dài 15-20 phút để não bộ tái tạo năng lượng.
                    - 🧠 **Active Recall (Chủ động gợi nhớ):** Tự đặt câu hỏi và tự trả lời tóm tắt kiến thức mà không nhìn tài liệu, giúp ghi nhớ sâu gấp nhiều lần việc đọc lại thụ động.
                    - 🗣️ **Phương pháp Feynman:** Thử giải thích một khái niệm phức tạp bằng ngôn từ đơn giản nhất cho một người chưa biết gì hiểu.
                    
                    💪 *Hãy chọn cho mình không gian yên tĩnh và uống đủ nước trong suốt buổi học nhé!*
                    """;
        }

        // 7. Lời chào hỏi & Giới thiệu
        if (containsAny(normalized, "xin chao", "hello", "hi ", "ban la ai", "tro ly gi", "giup gi", "gioi thieu")) {
            return """
                    Xin chào bạn! 👋 Tôi là **Trợ lý AI KLTN Social** — người bạn đồng hành thông minh trên mạng xã hội sinh viên.
                    
                    Tôi có thể hỗ trợ bạn:
                    - ✍️ **Sáng tạo nội dung:** Viết caption hay, tinh chỉnh văn phong bài viết, sửa lỗi ngữ pháp.
                    - 🏷️ **Hashtag:** Gợi ý hashtag thịnh hành, chuẩn SEO mạng xã hội theo từng chủ đề.
                    - 💬 **Gợi ý bình luận:** Phản hồi thông minh, tích cực và tự nhiên.
                    - 📖 **Học tập & Đồ án:** Hướng dẫn làm đồ án, bảo vệ khóa luận, giải đáp công nghệ & lập trình.
                    
                    Bạn hãy nhập câu hỏi hoặc bấm vào các gợi ý nhanh bên dưới để bắt đầu nhé! 🚀
                    """;
        }

        // 8. Phản hồi chung, chuyên nghiệp
        return String.format("""
                Cảm ơn câu hỏi của bạn về: **"%s"** ✨
                
                Dưới đây là một số thông tin và gợi ý hữu ích:
                - 🎯 **Trọng tâm:** Bạn có thể áp dụng các giải pháp thực tế, bám sát mục tiêu học tập và chia sẻ tích cực trên cộng đồng.
                - 💡 **Ý tưởng phát triển:** Đừng ngần ngại chia sẻ quan điểm của bạn trên bảng tin KLTN Social kèm hashtag phù hợp để kết nối thêm nhiều bạn bè có cùng sở thích.
                - 🤝 **Hỗ trợ thêm:** Bạn có thể yêu cầu tôi viết caption, gợi ý hashtag hoặc hỗ trợ kỹ thuật chi tiết hơn về chủ đề này!
                """, message);
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
