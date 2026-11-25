---
title: Trợ lý AI, kiểm duyệt tự động và tóm tắt bài dài
slug: tro-ly-ai
audience: user
tags: [trợ lý AI, AI assistant, chatbot, kiểm duyệt tự động, tóm tắt AI, gợi ý caption, hashtag, dịch bài viết, tiểu sử, bio, Gemini, nguồn tham khảo, giới hạn tần suất]
updated: 2026-11-19
---

# Trợ lý AI, kiểm duyệt tự động và tóm tắt bài dài

Tài liệu này nói về các tính năng dùng trí tuệ nhân tạo (AI) của KLTN Social: kiểm duyệt tự động bài viết mới, tóm tắt bài dài, các tiện ích hỗ trợ soạn nội dung và chatbot "Trợ lý AI". AI do Google Gemini cung cấp. Kết quả của AI chỉ mang tính tham khảo và có thể sai sót.

## Các tính năng của Trợ lý AI

KLTN Social có các tính năng AI sau (cần đăng nhập để dùng các tiện ích cho người dùng):
- Kiểm duyệt tự động bài viết mới (chạy ngầm, không cần bấm gì).
- Tóm tắt tự động bài viết dài hơn 1000 ký tự ("Bản tóm tắt bằng AI").
- Trong form tạo bài, nút "Trợ lý AI" với 5 tab: "Viết Caption", "Trau chuốt câu từ", "Gợi ý Hashtag", "Độ lan tỏa" và "Kiểm tra an toàn".
- "Dịch bài viết" cho bài khác ngôn ngữ giao diện.
- "Gợi ý bình luận nhanh" khi viết bình luận.
- "Gợi ý AI" tiểu sử trên trang cá nhân.
- "AI Tóm tắt tin nhắn" trong khung chat.
- Chatbot nổi "Trợ lý AI" để hỏi đáp và nhờ viết nội dung.

AI không tự đăng bài, không tự bình luận và không tự sửa hồ sơ thay bạn: mọi kết quả chỉ được điền vào ô soạn để bạn chỉnh sửa và tự bấm đăng.

## Kiểm duyệt tự động bài viết mới bằng AI

Khi bạn đăng bài có nội dung chữ, bài hiển thị bình thường ngay lập tức, rồi hệ thống AI kiểm tra nội dung chữ ngay sau đó (chạy nền, thường trong vài giây, có thể lâu hơn khi dịch vụ AI chậm). Mô hình Gemini chấm điểm độc hại từ 0 đến 1, phân loại (tục tĩu, thù ghét, quấy rối/xúc phạm, nội dung 18+, bạo lực/đe dọa, spam/lừa đảo/cờ bạc), mức độ (Thấp, Trung bình, Cao, Nghiêm trọng) và nêu lý do ngắn.

Bài bị coi là nghi vấn khi điểm từ 0.40 trở lên, hoặc AI đánh dấu độc hại, hoặc AI đề xuất cảnh báo, ẩn hay xóa. Bài an toàn giữ nguyên và (nếu đủ dài) được chuyển sang bước tóm tắt. Mỗi bài chỉ được đánh giá một lần khi đăng.

Lưu ý quan trọng: AI không bao giờ tự xóa bài mới đăng. Với bài nghi vấn, AI chỉ tạm ẩn và chuyển cho kiểm duyệt viên quyết định. Quản trị viên có thể tắt kiểm duyệt tự động bằng cài đặt hệ thống "Tự động kiểm duyệt bài viết bằng AI"; khi tắt, bài mới được coi là an toàn. AI cũng chấm điểm các bài bị người dùng báo cáo (xem tài liệu về báo cáo vi phạm).

## Bộ lọc từ khóa nhanh và khi nào nó thay thế AI

Bộ lọc từ khóa nhanh là lớp kiểm tra dự phòng, chỉ quyết định khi mô hình AI không dùng được: chưa cấu hình khóa API, mọi mô hình trả lỗi, hết hạn mức hoặc phản hồi quá 10 giây. Bộ lọc so nội dung với từ điển từ nhạy cảm do hệ thống quản lý (danh sách này không công khai).

Cách bộ lọc hoạt động:
- Chỉ khớp nguyên từ và đúng dấu thanh, dấu mũ, nên từ lành mạnh như "buổi" không bị nhầm với từ tục.
- Chữ gõ không dấu khớp với dạng không dấu của từ khóa chỉ được coi là bằng chứng yếu hơn (hạ một bậc mức độ), nên một số câu gõ hoàn toàn không dấu có thể bị bỏ sót khi AI không hoạt động.
- Thẻ gắn tên bạn bè (@tên) được bỏ qua, không bị coi là từ xấu.
- Điểm theo mức từ khóa: Nghiêm trọng từ 0.85, Cao từ 0.65, Trung bình từ 0.40.

AI có thể đề xuất từ nhạy cảm mới; các từ này phải được quản trị viên duyệt (trạng thái chờ duyệt) trước khi được dùng để lọc.

## Bài viết bị AI nghi vấn: tạm ẩn và hàng chờ kiểm duyệt viên

Khi AI nghi vấn bài viết mới đăng, bài bị tạm ẩn (người khác không thấy) nhưng chưa bị xóa. Điều xảy ra:
- Bài được đưa vào hàng chờ của kiểm duyệt viên với nguồn "AI gắn cờ" (trạng thái "Đang xem xét"), kèm loại vi phạm, mức độ, phần trăm nghi vấn và giải thích của AI.
- Tác giả nhận thông báo "Bài viết của bạn đang chờ kiểm duyệt": bài bị tạm ẩn vì có dấu hiệu vi phạm, kiểm duyệt viên sẽ xem xét và phản hồi, và có thể kháng nghị nếu cho rằng nhầm lẫn.
- Tác giả thấy bài trong "Hộp thư hỗ trợ", tab "Bài viết bị kiểm duyệt", nơi có nút "Kháng nghị quyết định".
- Điểm uy tín chưa bị trừ cho đến khi kiểm duyệt viên xác nhận bằng cách ẩn hoặc gỡ bài.

Kiểm duyệt viên có thể chọn "Bỏ qua (Hợp lệ)" để bài hiển thị lại, hoặc "Ẩn bài viết"/"Gỡ bài viết" để xác nhận vi phạm.

## Tóm tắt tự động bài viết dài

Bài viết dài hơn 1000 ký tự (sau khi bỏ khoảng trắng đầu cuối) và an toàn sẽ được AI tóm tắt sau khi đăng, thành 1-2 câu hoặc 2-3 gạch đầu dòng bằng tiếng Việt. Bản tóm tắt được lưu cùng bài. Bài ngắn hơn hoặc bằng 1000 ký tự, bài bị AI gắn cờ và bài rỗng không được tóm tắt tự động.

Người xem thấy nút "Bản tóm tắt bằng AI" dưới nội dung bài dài. Bấm vào để mở khối "Bản tóm tắt bằng AI:", bấm lại ("Ẩn bản tóm tắt") để ẩn. Với bài cũ chưa có bản tóm tắt lưu sẵn, bấm nút sẽ tạo bản tóm tắt ngay ("Đang tóm tắt..."); nếu lỗi, hệ thống báo "Không thể tóm tắt bài viết lúc này. Vui lòng thử lại sau."

Khi tác giả sửa nội dung bài, bản tóm tắt cũ bị xóa vì không còn đúng và không tự tạo lại; người xem vẫn bấm nút để tạo bản tóm tắt theo yêu cầu.

## Soạn bài với Trợ lý AI: caption, trau chuốt, hashtag, độ lan tỏa

Trong form tạo bài, mục "Thêm vào bài viết của bạn" có nút "Trợ lý AI". Panel có các tab:
- "Viết Caption": nhập chủ đề hoặc ý tưởng (hoặc dùng nội dung đang soạn), chọn giọng điệu (tự nhiên gần gũi, hài hước dí dỏm, truyền cảm hứng, chuyên nghiệp trang trọng, ngắn gọn ấn tượng), bấm "Tạo 3 gợi ý bài viết ngay", rồi "Chọn" một gợi ý để điền vào ô soạn.
- "Trau chuốt câu từ": sửa chính tả, ngữ pháp, teencode và nâng văn phong theo phong cách chọn, bấm "Trau chuốt văn phong ngay", rồi "Áp dụng".
- "Gợi ý Hashtag": bấm "Tạo 6 Hashtag thịnh hành", rồi "Chèn vào bài", chèn từng hashtag hoặc "Sao chép tất cả".
- "Độ lan tỏa": bấm "Phân tích tiềm năng tương tác bài viết" để xem điểm tương tác dự kiến trên 100, cảm xúc chủ đạo, "Vibe bài viết" và các mẹo cải thiện.

Kết quả chỉ để tham khảo; bạn tự chỉnh sửa và đăng.

## Kiểm tra an toàn bài viết trước khi đăng

Tab "Kiểm tra an toàn" cho bạn thử xem bài đang soạn có chứa từ ngữ xúc phạm, quấy rối, thù ghét hay nội dung không phù hợp không. Nhập nội dung rồi bấm "Kiểm tra mức độ an toàn bài viết". Kết quả hiện "Nội dung an toàn, văn minh & tích cực!" hoặc "Cảnh báo: Nội dung có thể vi phạm chuẩn mực", kèm "Mức độ", lý do và các từ khóa phát hiện.

Kết quả này chỉ mang tính tham khảo và không chặn việc đăng bài. Sau khi đăng, bài vẫn đi qua kiểm duyệt tự động như mọi bài khác. Cần có nội dung chữ để kiểm tra.

## Dịch bài viết bằng AI

Dưới bài viết có nút "Dịch bài viết" khi nội dung rõ ràng không cùng ngôn ngữ với giao diện của bạn. Trình duyệt tự nhận diện ngôn ngữ (không gọi AI) nên bài quá ngắn, chỉ có số, emoji, đường dẫn hoặc hashtag sẽ không hiện nút. AI dịch sang ngôn ngữ giao diện của bạn (tiếng Việt hoặc tiếng Anh). Trong lúc dịch nút hiện "Đang dịch..."; sau khi dịch, bấm "Xem bản gốc" để quay lại.

Nếu dịch lỗi, hệ thống báo "Không thể dịch bài viết lúc này. Vui lòng thử lại sau." Trong menu tùy chọn của bài có mục "Tắt bản dịch" hoặc "Bật bản dịch" để bật/tắt việc dịch cho bài đó.

## Gợi ý trả lời bình luận nhanh

Khi viết bình luận, bạn bấm "Gợi ý bình luận nhanh" (đổi thành "Ẩn gợi ý AI" khi đang mở) để AI đưa ra 3 câu trả lời ngắn gọn, lịch sự dựa trên nội dung bài viết, hoặc dựa trên người bạn đang trả lời. Bấm một gợi ý để điền vào ô bình luận, rồi bạn chỉnh và gửi. Có nút "Đổi gợi ý khác" để lấy gợi ý mới. Ở phần bình luận ngay dưới bài trên bảng tin cũng có nút "Gợi ý trả lời nhanh bằng AI" với tác dụng tương tự.

## Gợi ý tiểu sử (bio) bằng AI

Khi bạn chỉnh sửa tiểu sử ở mục "Giới thiệu" trong trang cá nhân của mình, có nút "Gợi ý AI". Hộp thoại "Gợi ý tiểu sử bằng AI" hỏi "Chuyên ngành / Công việc hiện tại", "Sở thích / Đam mê nổi bật" và "Phong cách mong muốn" (Năng động & Trẻ trung, Chuyên nghiệp & Tinh tế, Tối giản & Sâu lắng). Bấm "Tạo 3 mẫu tiểu sử hay" để nhận 3 mẫu ngắn (AI được yêu cầu tối đa khoảng 100 ký tự). Bấm "Dùng" để điền vào ô tiểu sử, rồi lưu. Ô tiểu sử trên giao diện cho nhập tối đa 101 ký tự.

## Tóm tắt tin nhắn cuộc trò chuyện bằng AI

Trong khung chat có nút "AI Tóm tắt tin nhắn (kèm ảnh)" (hoặc "AI Tóm tắt N tin nhắn chưa đọc (kèm ảnh)" khi có tin chưa đọc). AI tóm tắt nội dung chính, mô tả ảnh đính kèm và liệt kê "Nội dung chính / Việc cần làm". Nếu có tin chưa đọc, hệ thống tóm tắt các tin chưa đọc kèm 3 tin trước đó để làm ngữ cảnh; nếu không thì tóm tắt tối đa 30 tin gần nhất. Tối đa 3 ảnh được phân tích.

Bạn có thể bấm "Sao chép tóm tắt" hoặc "Đóng tóm tắt". Nếu chưa có tin nhắn, hệ thống báo "Chưa có tin nhắn nào để tóm tắt". Nếu lỗi: "Không thể tạo tóm tắt vào lúc này".

## Trò chuyện với Trợ lý AI và nguồn tham khảo [1], [2]

Chatbot nổi "Trợ lý AI" nằm ở góc dưới bên trái màn hình sau khi đăng nhập (không hiện ở trang /moderation và /admin). Bạn nhắn câu hỏi hoặc dùng gợi ý nhanh "Viết caption hay", "Hashtag thịnh hành", "Ý tưởng đăng bài", "Hỏi đáp học tập". Nút "Làm mới cuộc trò chuyện" xóa nội dung chat.

Với câu hỏi đủ rõ (ít nhất 8 ký tự và 3 từ, không phải câu chào xã giao), trợ lý tìm các bài viết liên quan mà bạn được phép xem rồi dùng làm tư liệu, tối đa 4 nguồn. Câu trả lời ghi số nguồn [1], [2] và dưới tin nhắn có danh sách "Nguồn tham khảo" đánh số tương ứng; bấm vào một nguồn để mở bài viết đó. Khi câu hỏi về cách dùng KLTN Social, trợ lý cũng có thể trích dẫn tài liệu hướng dẫn của hệ thống. Nếu tư liệu không đủ, trợ lý nói chưa tìm thấy và trả lời bằng kiến thức chung. Trợ lý nhớ tối đa 10 tin gần nhất trong cuộc trò chuyện.

## Quyền riêng tư khi dùng Trợ lý AI

Khi trợ lý tìm bài viết làm tư liệu, yêu cầu tìm kiếm được gửi kèm tài khoản của người hỏi, nên chỉ những bài viết mà người hỏi được phép xem mới trở thành nguồn. Bài riêng tư của người khác không xuất hiện trong nguồn tham khảo của bạn.

Máy chủ AI không lưu lại kết quả gợi ý, bản dịch hay câu trả lời. Lịch sử trò chuyện với chatbot chỉ nằm tạm trên trình duyệt (tải lại trang là mất). Để AI xử lý, nội dung bạn nhập (bài viết, tin nhắn, ảnh trong chat khi tóm tắt) được gửi tới dịch vụ Google Gemini; vì vậy đừng nhập thông tin nhạy cảm như mật khẩu hay số thẻ. Tất cả tiện ích AI đều yêu cầu đăng nhập.

## Giới hạn tần suất và khi AI không khả dụng

Mọi yêu cầu AI (gợi ý, dịch, tóm tắt, chat, kiểm tra an toàn) bị giới hạn tần suất theo tài khoản và theo địa chỉ IP bằng cơ chế xô token. Mặc định trong mã nguồn: tối đa 5 yêu cầu liên tiếp, sau đó được nạp lại 1 yêu cầu mỗi 10 giây; môi trường triển khai có thể đặt khác (môi trường phát triển của dự án hiện đặt 30 yêu cầu, nạp 1 yêu cầu mỗi 2 giây). Vượt giới hạn, máy chủ trả mã 429 với thông báo "Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ N giây trước khi thử lại." và giao diện có thể chỉ hiện danh sách gợi ý trống hoặc lỗi chung.

Khi AI không khả dụng (chưa cấu hình khóa, mô hình lỗi, hết hạn mức hoặc quá thời gian), hệ thống dùng bộ xử lý dự phòng: gợi ý mẫu cho caption, hashtag, trả lời và tiểu sử; chỉnh sửa cơ bản (đổi teencode thông dụng, viết hoa đầu câu, thêm dấu câu) cho "Trau chuốt"; hai câu đầu cho tóm tắt theo yêu cầu; phản hồi theo luật cho chatbot. Với bản dịch, nếu lỗi bạn nhận thông báo không thể dịch. Kiểm duyệt tự động chuyển sang bộ lọc từ khóa. Tóm tắt tự động của bài dài bị bỏ qua nên bài không có bản tóm tắt lưu sẵn.

## Câu hỏi thường gặp

### AI có kiểm duyệt bài viết của tôi trước khi đăng không?

Không chặn trước khi đăng. Bài được đăng ngay, rồi AI kiểm tra nội dung chữ ngay sau đó; nếu nghi vấn, bài bị tạm ẩn và chuyển cho kiểm duyệt viên. Bạn có thể bấm "Kiểm tra mức độ an toàn bài viết" trong tab "Kiểm tra an toàn" để thử trước.

### Vì sao tôi nhận thông báo "Bài viết của bạn đang chờ kiểm duyệt"?

AI thấy bài của bạn có dấu hiệu vi phạm nên đã tạm ẩn bài và chuyển cho kiểm duyệt viên xem xét. Bạn có thể vào Hộp thư hỗ trợ, tab "Bài viết bị kiểm duyệt", bấm "Kháng nghị quyết định" nếu cho rằng đây là nhầm lẫn.

### Bài bị AI tạm ẩn có làm giảm điểm uy tín của tôi không?

Chưa. Điểm uy tín chỉ bị trừ khi kiểm duyệt viên xác nhận bằng cách ẩn hoặc gỡ bài. Nếu kiểm duyệt viên chọn "Bỏ qua (Hợp lệ)", bài hiển thị lại và không bị trừ điểm.

### AI có kiểm duyệt ảnh, video, bình luận hay chỉnh sửa bài không?

Chưa. Kiểm duyệt tự động chỉ xem xét nội dung chữ của bài viết mới đăng. Bài chỉ có ảnh/video không chữ, bình luận và nội dung sau khi chỉnh sửa không được AI kiểm tra tự động; chúng chỉ bị xử lý khi có người báo cáo.

### Bài viết dài bao nhiêu ký tự thì có tóm tắt AI?

Dài hơn 1000 ký tự. Bài từ 1000 ký tự trở xuống không có nút "Bản tóm tắt bằng AI".

### Vì sao bài dài của tôi chưa có bản tóm tắt lưu sẵn?

Bản tóm tắt tạo sau khi đăng nên có thể chậm vài giây; AI không tóm tắt bài bị gắn cờ, hoặc khi AI lỗi. Sau khi bạn sửa nội dung, bản tóm tắt cũ bị xóa. Người xem vẫn bấm "Bản tóm tắt bằng AI" để tạo bản tóm tắt theo yêu cầu.

### Trợ lý AI có lưu lại cuộc trò chuyện của tôi không?

Máy chủ AI không lưu lại kết quả. Lịch sử chat chỉ nằm tạm trên trình duyệt và mất khi tải lại trang hoặc bấm "Làm mới cuộc trò chuyện". Nội dung bạn nhập vẫn được gửi tới Google Gemini để xử lý.

### Trợ lý AI có đọc bài viết riêng tư của người khác không?

Không. Trợ lý chỉ dùng các bài viết mà chính bạn được phép xem làm tư liệu tham khảo. Bài riêng tư hoặc bài bạn không có quyền xem không xuất hiện trong nguồn.

### Số [1], [2] trong câu trả lời của trợ lý là gì?

Đó là số thứ tự nguồn tham khảo: các bài viết (hoặc tài liệu hướng dẫn) mà trợ lý dùng để trả lời. Danh sách "Nguồn tham khảo" dưới tin nhắn đánh số tương ứng; bấm vào nguồn bài viết để mở bài đó.

### Vì sao tôi thấy lỗi hoặc không có gợi ý khi dùng AI liên tục?

Có thể bạn đã vượt giới hạn tần suất AI. Hệ thống báo "Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ N giây trước khi thử lại." Hãy chờ vài giây rồi thử lại.

### Khi AI không hoạt động thì chuyện gì xảy ra?

Hệ thống dùng bộ xử lý dự phòng (gợi ý mẫu, văn bản gốc, bộ lọc từ khóa cho kiểm duyệt). Bản dịch và bản tóm tắt bài dài có thể không được tạo; hệ thống báo lỗi hoặc bỏ qua và bạn có thể thử lại sau.

### Vì sao không thấy nút "Dịch bài viết"?

Nút chỉ hiện khi bài viết rõ ràng không cùng ngôn ngữ với giao diện của bạn. Bài quá ngắn, chỉ có số, emoji, đường dẫn, hashtag hoặc đã cùng ngôn ngữ thì không hiện nút. Bạn cũng có thể đã chọn "Tắt bản dịch" cho bài đó.

### AI có tự đăng bài hay bình luận thay tôi không?

Không. Các gợi ý chỉ được điền vào ô soạn bài, bình luận hoặc tiểu sử để bạn chỉnh sửa; bạn phải tự bấm đăng hoặc lưu.

### Kết quả "Kiểm tra mức độ an toàn" có chặn tôi đăng bài không?

Không. Kết quả chỉ để bạn tham khảo. Bạn vẫn đăng được, nhưng bài vẫn đi qua kiểm duyệt tự động sau khi đăng.
