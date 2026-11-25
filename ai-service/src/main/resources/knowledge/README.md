# Kho tri thức của KLTN Social (nguồn cho vector store của trợ lý AI)

Thư mục này là **nguồn duy nhất** của kho tri thức mà trợ lý AI dùng để trả lời câu hỏi về cách dùng và quy định của KLTN Social.
Mỗi file `.md` (trừ `README.md` và file bắt đầu bằng `_`) được `KnowledgeIngestionService` đọc lúc ai-service khởi động,
cắt thành các **đoạn (chunk)**, tạo vector embedding 768 chiều bằng Gemini và lưu vào bảng `knowledge_chunks` (pgvector).
Khi người dùng hỏi trợ lý, hệ thống tìm các đoạn gần nghĩa nhất rồi đưa cho mô hình làm tư liệu, và trả lời kèm nguồn `[1]`, `[2]`.

Sửa file ở đây rồi khởi động lại ai-service (hoặc gọi `POST /api/v1/ai/knowledge/reindex` bằng tài khoản ADMIN). Chỉ những đoạn có nội dung
thay đổi mới bị tạo lại vector (so sánh bằng mã băm), nên sửa ít thì tốn ít lượt gọi Gemini.

## Cách một file được cắt thành đoạn

1. Phần đầu file là khối front matter giữa hai dòng `---` (tiêu đề, loại người đọc, thẻ). Không đưa vào nội dung đoạn.
2. Dòng `# Tiêu đề` là tên tài liệu. Mỗi mục `##` là **một chủ đề độc lập**; mỗi mục con `###` là một đoạn riêng.
3. Mỗi đoạn được nhúng cùng "đường dẫn" của nó: `Tài liệu > Mục > Mục con`, rồi mới đến nội dung. Đoạn quá ngắn (dưới ~250 ký tự)
   được gộp vào đoạn liền trước cùng mục; đoạn dài hơn ~1800 ký tự bị cắt theo đoạn văn.

## Quy tắc viết (để tìm kiếm đúng)

- **Mỗi mục `##` tự đứng được**: đọc riêng nó vẫn hiểu. Câu đầu tiên trả lời thẳng vào chủ đề. Không viết "như đã nói ở trên", "xem phần trước";
  nhắc lại tên đối tượng ("Bài viết trong nhóm...", "Điểm uy tín...") thay vì dùng "nó", "đó".
- **Tiêu đề mục là cách người dùng sẽ hỏi hoặc gọi tên việc**: "Cách đăng ký tài khoản", "Giới hạn dung lượng ảnh và video", "Khi nào tài khoản bị khóa".
- **Viết cụ thể**: tên nút/menu đúng như trên giao diện (tiếng Việt, có dấu), con số, thời hạn, giới hạn, vai trò được phép, điều kiện, và nguyên văn thông báo lỗi
  người dùng sẽ thấy. Nêu rõ điều gì **chưa được hỗ trợ**.
- **Từ đồng nghĩa**: lần đầu dùng một thuật ngữ, ghi kèm cách gọi khác người dùng hay gõ, ví dụ "xác thực hai bước (MFA, 2FA)", "tin (story)", "bạn bè (friend)".
- **Không bịa**. Chỉ ghi điều đã kiểm tra trong code hoặc giao diện. Không chắc thì bỏ qua, hoặc ghi "chưa xác định".
- **Không đưa** mật khẩu, khóa bí mật, email thật, địa chỉ nội bộ hay dữ liệu cá nhân.
- Mỗi file kết thúc bằng mục `## Câu hỏi thường gặp`, trong đó **mỗi câu hỏi là một mục `###`** (tiêu đề là câu hỏi đúng như người dùng sẽ gõ, phần
  thân là câu trả lời đầy đủ 1-4 câu, tự đứng được). Đây là phần tìm kiếm trúng nhất nên 8-12 câu cho mỗi file.
- Độ dài mục `##` lý tưởng 600-1500 ký tự (khoảng 100-250 từ). Dùng danh sách gạch đầu dòng cho các bước hoặc điều kiện.

## Khung mẫu

```markdown
---
title: Tên tài liệu
slug: ten-tai-lieu-khong-dau
audience: user            # user | moderator | admin | developer
tags: [từ khóa, đồng nghĩa, ...]
updated: 2026-11-19
---

# Tên tài liệu

Một đoạn mở đầu 2-3 câu nói tài liệu này nói về gì, ai dùng.

## Cách làm việc X

Câu trả lời thẳng. Các bước:
1. ...
2. ...

Giới hạn và lưu ý: ...

## Câu hỏi thường gặp

### Làm sao để X?

Trả lời đầy đủ, tự đứng được.
```

## Tên vai trò và thuật ngữ thống nhất

Khách vãng lai (Guest), Người dùng (User), Kiểm duyệt viên (Moderator), Quản trị viên (Admin); bài viết (post), tin (story), nhóm cộng đồng (group),
điểm uy tín (reputation score), bảng tin (feed), trợ lý AI (AI assistant), kháng nghị (appeal), báo cáo vi phạm (report).

## Danh mục tài liệu

| File | Nội dung |
|---|---|
| `00-tong-quan.md` | KLTN Social là gì, vai trò, các khu vực chính, thuật ngữ |
| `01-tai-khoan-va-bao-mat.md` | Đăng ký, đăng nhập, OTP, MFA, thiết bị, quên và đổi mật khẩu |
| `02-ho-so-va-quyen-rieng-tu.md` | Hồ sơ cá nhân, ảnh đại diện, quyền riêng tư |
| `03-ban-be-theo-doi-chan.md` | Kết bạn, theo dõi, chặn, gợi ý kết bạn |
| `04-bai-viet-va-tuong-tac.md` | Đăng, sửa, xóa, ghim, lưu trữ, cảm xúc, bình luận, chia sẻ, lưu |
| `05-bang-tin-story-watch-tim-kiem.md` | Bảng tin, tin (story), Watch, tìm kiếm |
| `06-nhom-cong-dong.md` | Tạo, tham gia, quản lý nhóm |
| `07-nhan-tin-cuoc-goi-live.md` | Nhắn tin, gọi thoại/video, phát trực tiếp, thông báo |
| `08-diem-uy-tin-nha-sang-tao.md` | Điểm uy tín, bậc, hạn chế, khóa; chế độ nhà sáng tạo |
| `09-bao-cao-kiem-duyet-khang-nghi.md` | Báo cáo vi phạm, kiểm duyệt, kháng nghị |
| `10-tro-ly-ai.md` | Trợ lý AI, kiểm duyệt tự động, tóm tắt bài dài |
| `11-quan-tri-he-thong.md` | Việc của Quản trị viên và Kiểm duyệt viên |
| `12-kien-truc-ky-thuat.md` | Kiến trúc, dịch vụ, luồng sự kiện, cơ sở dữ liệu (cho lập trình viên) |
