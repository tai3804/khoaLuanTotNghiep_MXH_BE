---
title: Quản trị hệ thống
slug: quan-tri-he-thong
audience: admin
tags: [quản trị viên, admin, kiểm duyệt viên, moderator, khóa tài khoản, mở khóa, đổi vai trò, dashboard, thống kê, từ khóa cấm, từ khóa AI, cấu hình hệ thống, chế độ bảo trì, thông báo hệ thống, nhật ký quản trị, audit log, quản lý nhóm]
updated: 2026-11-19
---

# Quản trị hệ thống

Tài liệu này mô tả việc của Quản trị viên (Admin) và Kiểm duyệt viên (Moderator) trên KLTN Social: quản lý người dùng, xem thống kê, quản lý từ khóa cấm và từ khóa AI, quản lý nhóm, cấu hình hệ thống, chế độ bảo trì và nhật ký. Các chức năng quản trị đều do admin-service cung cấp qua API (đi qua API Gateway, đường dẫn bắt đầu bằng `/api/v1/admin/`); riêng khu kiểm duyệt báo cáo có giao diện web.

## Quản trị viên (Admin) và Kiểm duyệt viên (Moderator) khác nhau thế nào, ai vào được trang nào

Quản trị viên (Admin) có toàn quyền quản trị; Kiểm duyệt viên (Moderator) chỉ có nhóm quyền kiểm duyệt nội dung và xem thống kê. Quyền lấy từ vai trò trong token đăng nhập (ROLE_ADMIN, ROLE_MODERATOR, ROLE_USER); tài khoản Admin có cả ROLE_ADMIN và ROLE_USER.

- **Admin và Moderator đều dùng được**: xem danh sách người dùng; dashboard và phân tích; quản lý từ khóa cấm của hệ thống; quản lý từ khóa AI; gửi thông báo hệ thống; xem, xử lý báo cáo vi phạm, xem nhật ký kiểm duyệt, duyệt kháng nghị.
- **Chỉ Admin**: khóa, mở khóa, đổi vai trò, tạo tài khoản, đặt lại mật khẩu, thu hồi phiên đăng nhập; xem và giải tán nhóm; xem và sửa cấu hình hệ thống; dọn bộ nhớ đệm; xem nhật ký thao tác quản trị; kiểm tra tình trạng dịch vụ; nạp lại kho tri thức của trợ lý AI.
- Người dùng thường gọi API quản trị sẽ nhận HTTP 403 với thông báo "Bạn không có quyền thực hiện thao tác này"; chưa đăng nhập thì nhận 401.

Trên giao diện web, đường dẫn `/moderation` và `/admin` đều mở màn hình "Trung tâm kiểm duyệt" với ba mục: "Hàng chờ báo cáo", "Nhật ký & Hoàn tác", "Kháng nghị". Chỉ tài khoản Admin thấy nút "Hệ thống Admin" ở thanh bên (nút này dẫn tới `/admin`, hiện cùng màn hình). Tài khoản không có quyền thấy trang "Khu vực Kiểm duyệt viên" với dòng "Tài khoản hiện tại chưa được cấp quyền Moderator hoặc Admin". Giao diện web hiện chưa có màn hình riêng cho dashboard, quản lý người dùng, từ khóa, nhóm, cấu hình hay nhật ký quản trị; các việc đó thực hiện bằng API.

## Quản lý người dùng: xem danh sách và tìm kiếm

Admin và Moderator xem danh sách người dùng bằng `GET /api/v1/admin/users`. admin-service lấy hồ sơ từ user-service, rồi lấy email, trạng thái (status) và vai trò từ auth-service để ghép vào từng người; vai trò hiển thị theo thứ tự ưu tiên ADMIN, MODERATOR, USER.

- API trả toàn bộ người dùng trong một lần, không có tham số tìm kiếm hay phân trang; việc lọc theo tên, email, vai trò phải làm ở phía gọi API.
- Trạng thái tài khoản (auth-service) gồm ACTIVE, INACTIVE, LOCKED, BANNED.
- Nếu user-service lỗi, danh sách trả về rỗng. Nếu auth-service lỗi, danh sách vẫn trả hồ sơ nhưng mặc định status ACTIVE và vai trò USER, nên số liệu có thể sai tạm thời.

## Khóa và mở khóa tài khoản

Chỉ Admin khóa hoặc mở khóa tài khoản. Khóa: `PUT /api/v1/admin/users/{userId}/ban` (có thể kèm `{"reason": "..."}`); mở khóa: `PUT /api/v1/admin/users/{userId}/unban`.

Khi khóa, auth-service:
1. Đặt trạng thái BANNED và tăng số phiên bản token (tokenVersion) lên 1.
2. Xóa mọi thiết bị đã đăng nhập của tài khoản.
3. Ghi phiên bản mới vào Redis (giữ 7 ngày) để mọi token cũ bị từ chối ngay.
4. Gửi thông báo trong ứng dụng có tiêu đề "ACCOUNT_BANNED", nội dung "Tài khoản của bạn đã bị khóa." để buộc đăng xuất.

Tài khoản BANNED đăng nhập hoặc làm mới token sẽ nhận HTTP 403: "Tài khoản của bạn đã bị cấm/khóa do vi phạm tiêu chuẩn cộng đồng." Nếu không nêu lý do, nhật ký ghi lý do mặc định "Vi phạm tiêu chuẩn cộng đồng". Mở khóa chỉ đặt lại trạng thái ACTIVE; người dùng đăng nhập lại bình thường. Nếu auth-service lỗi hoặc không tìm thấy người dùng thì không đổi gì và không ghi nhật ký.

Kiểm duyệt viên không khóa được tài khoản: hành động "Đề xuất cấm tài khoản" (BAN_USER) trong xử lý báo cáo chỉ được ghi vào nhật ký kiểm duyệt để Admin xem xét, không tự khóa ai.

## Đổi vai trò, tạo tài khoản, đặt lại mật khẩu, thu hồi phiên đăng nhập

Chỉ Admin dùng các API này (đều ghi nhật ký quản trị):

- **Đổi vai trò**: `PUT /api/v1/admin/users/{userId}/role` với `{"role": "ADMIN"}`, `"MODERATOR"` hoặc `"USER"`. Thiếu `role` thì hệ thống dùng USER. Admin nhận ROLE_ADMIN + ROLE_USER, Moderator nhận ROLE_MODERATOR + ROLE_USER. tokenVersion của người đó tăng nên người đó phải đăng nhập lại để quyền mới có hiệu lực.
- **Tạo tài khoản**: `POST /api/v1/admin/users` với email, mật khẩu (tối thiểu 6 ký tự), firstName, lastName, role, dateOfBirth, gender. Tài khoản tạo ra ở trạng thái ACTIVE. Email trùng trả 400 với `{"message": "Địa chỉ email này đã tồn tại trong hệ thống."}`.
- **Đặt lại mật khẩu**: `PUT /api/v1/admin/users/{userId}/reset-password` với `{"newPassword": "..."}`. Mật khẩu rỗng trả 400. Sau khi đặt, mọi phiên và thiết bị cũ của người đó bị thu hồi.
- **Thu hồi phiên**: `PUT /api/v1/admin/users/{userId}/revoke-sessions` buộc đăng xuất khỏi mọi thiết bị.

## Dashboard và thống kê tổng quan

Admin và Moderator xem số liệu vận hành bằng `GET /api/v1/admin/dashboard/stats`. Kết quả gồm: totalUsers (tổng người dùng), totalPosts (tổng bài viết), totalReports (tổng báo cáo), pendingReports (báo cáo đang chờ), newUsersToday, newPostsToday, resolvedReportsToday (báo cáo đã xử lý hoặc bỏ qua trong ngày), activeUsersNow (người dùng đang trực tuyến).

Ba biểu đồ kèm theo:
- `GET /api/v1/admin/dashboard/growth`: người dùng mới và tổng lũy kế trong 7 ngày gần nhất (nhãn ngày dạng dd/MM).
- `GET /api/v1/admin/dashboard/interactions`: số bài đăng mỗi ngày trong 7 ngày (các cột còn lại hiện bằng 0).
- `GET /api/v1/admin/dashboard/report-categories`: số báo cáo và phần trăm theo sáu nhóm: Spam / Quảng cáo, Nội dung thù ghét, Quấy rối / Đe dọa, Bạo lực / Phản cảm, Thông tin sai sự thật, Khác.

Lưu ý độ chính xác: số bài viết lấy từ danh sách bài viết mà post-service trả về (tối đa 100 bài mỗi lần), nên "bài mới hôm nay" và biểu đồ bài đăng chỉ tính trên 100 bài đầu. Dịch vụ nào lỗi thì chỉ số của dịch vụ đó hiện 0 và các chỉ số còn lại vẫn trả về.

## Phân tích: hashtag xu hướng, ma trận hoạt động, nhân khẩu học

Admin và Moderator có ba API phân tích dưới `/api/v1/admin/analytics`:

- `GET .../trends` (hashtag xu hướng): so số bài chứa hashtag giữa cửa sổ hiện tại và cửa sổ liền trước cùng độ dài. Hệ thống thử cửa sổ 24 giờ, 72 giờ rồi 168 giờ và dừng ở cửa sổ đầu tiên có ít nhất 5 lượt hashtag. Trả tối đa 20 hashtag, mỗi hashtag có số bài, số bài kỳ trước, phần trăm tăng trưởng, điểm bùng nổ; trạng thái VIRAL khi điểm bùng nổ từ 3 trở lên và có ít nhất 3 bài, ngược lại SAFE. Dữ liệu lấy từ tối đa 300 bài mới nhất (3 trang x 100).
- `GET .../heatmap` (ma trận hoạt động): bảng 7 ngày x 24 giờ cộng từ bài viết (1 điểm cho bài + lượt thích + bình luận + chia sẻ), báo cáo 30 ngày gần nhất và nhật ký kiểm duyệt; trả thêm ngày cao điểm, khung giờ cao điểm và tỉ lệ hoạt động buổi tối (18 giờ đến 23 giờ).
- `GET .../demographics`: tỉ lệ giới tính (Nam, Nữ, Khác), nhóm tuổi (Dưới 18 tuổi, 18 - 24 tuổi, 25 - 34 tuổi, 35+ tuổi, Chưa cập nhật ngày sinh), tỉ lệ tài khoản "Hoạt động bình thường" so với "Đang bị khóa" và tỉ lệ người dùng đang trực tuyến.

## Quản lý từ khóa cấm của hệ thống

Danh sách từ khóa cấm của hệ thống do admin-service lưu trong bảng `blacklisted_words`. Admin và Moderator dùng:

- `GET /api/v1/admin/settings/blacklist`: xem danh sách.
- `POST /api/v1/admin/settings/blacklist`: thêm một từ (nội dung yêu cầu là chính từ đó). Hệ thống cắt khoảng trắng, bỏ cặp dấu ngoặc kép bao ngoài, đổi sang chữ thường. Từ đã có thì trả lại mục cũ, không báo lỗi; từ rỗng thì không thêm.
- `DELETE /api/v1/admin/settings/blacklist/{id}`: gỡ một từ.

Mỗi thao tác ghi nhật ký quản trị với hành động ADD_BLACKLIST hoặc REMOVE_BLACKLIST. Lưu ý theo mã nguồn hiện tại: danh sách này chỉ được lưu và hiển thị, chưa có dịch vụ nào đọc nó để chặn bài viết. Việc chặn nội dung tự động dựa vào từ khóa nhạy cảm của ai-service (xem mục tiếp theo).

## Quản lý từ khóa nhạy cảm do AI (từ khóa AI)

Từ khóa nhạy cảm nằm ở ai-service (bảng `sensitive_keywords`) và là bộ lọc từ khóa mà kiểm duyệt bài viết dùng trước khi hỏi Gemini. Admin và Moderator quản lý bằng `/api/v1/ai/keywords`:

- `GET`: danh sách phân trang (tham số `keyword`, `category`, `status`, `page` bắt đầu từ 0, `size` mặc định 20, mới nhất trước).
- `POST`: thêm từ thủ công với `keyword`, `category` (PROFANITY, HATE_SPEECH, HARASSMENT, SEXUAL, VIOLENCE, SPAM), `severity` (LOW, MEDIUM, HIGH, CRITICAL); có hiệu lực ngay (trạng thái ACTIVE). Từ phải dài 2 đến 50 ký tự.
- `POST /{id}/approve`: duyệt từ do AI tự học, chuyển sang ACTIVE và nạp vào bộ lọc.
- `POST /{id}/whitelist`: đưa vào danh sách an toàn (WHITELISTED), bỏ khỏi bộ lọc.
- `DELETE /{id}`: xóa hẳn từ khóa.

Từ do AI trích xuất khi chấm bài mới có trạng thái PENDING_REVIEW (độ tin cậy 0,85) và chưa được dùng để lọc cho tới khi được duyệt. Có thể tắt việc tự học bằng biến `AI_AUTO_LEARN_ENABLED=false`. Trang này không liệt kê nội dung từ khóa; hãy xem trực tiếp bằng API.

## Quản lý nhóm cộng đồng toàn hệ thống

Chỉ Admin quản lý nhóm toàn hệ thống: `GET /api/v1/admin/groups` trả danh sách mọi nhóm (user-service lỗi thì trả danh sách rỗng, không báo lỗi). `DELETE /api/v1/admin/groups/{groupId}` giải tán nhóm vi phạm: nhóm được đánh dấu đã xóa (xóa mềm) rồi user-service phát sự kiện `group.deleted` để post-service xử lý nội dung của nhóm. Thao tác ghi nhật ký DELETE_GROUP với chi tiết "Giải tán hội nhóm vi phạm". Nhóm không tồn tại thì báo lỗi không tìm thấy nhóm.

## Cấu hình hệ thống, dọn bộ nhớ đệm và kiểm tra dịch vụ

Admin xem cấu hình bằng `GET /api/v1/admin/settings/system` và sửa bằng `PUT /api/v1/admin/settings/system` (gửi các cặp khóa và giá trị chuỗi). Sáu khóa mặc định:

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| maintenance_mode | false | Chế độ bảo trì hệ thống |
| allow_registration | true | Cho phép đăng ký tài khoản mới |
| ai_moderation_enabled | true | Tự động kiểm duyệt bài viết bằng AI |
| max_upload_size_mb | 25 | Dung lượng tệp tải lên tối đa (MB) |
| system_announcement | rỗng | Thông báo banner toàn hệ thống |
| max_reports_auto_hide | 5 | Số báo cáo vi phạm trước khi tạm ẩn bài |

Khóa nào thực sự có tác dụng ở đâu: `ai_moderation_enabled` do ai-service đọc lại mỗi 30 giây; `max_reports_auto_hide` dùng khi có báo cáo mới (đủ ngưỡng báo cáo đang chờ trên cùng một nội dung thì bài bị tạm ẩn, bình luận bị gỡ, một lần duy nhất, do tài khoản hệ thống thực hiện); `maintenance_mode`, `allow_registration`, `max_upload_size_mb` chỉ được giao diện web áp dụng, các API phía sau không kiểm tra. Mỗi lần lưu ghi nhật ký UPDATE_SYSTEM_CONFIG.

Các thao tác Admin khác trong cùng nhóm:
- `POST /api/v1/admin/settings/clear-cache?type=all|blacklist|posts|token`: dọn Redis theo nhóm (mặc định `all`; `token` không xóa trạng thái thu hồi token). Ghi nhật ký CLEAR_CACHE.
- `GET /api/v1/admin/settings/service-health`: kiểm tra 12 thành phần (Eureka, API Gateway, Auth, Post, Notification, Media, User, Call, Chat, Feed, Admin, AI) và trả UP hoặc DOWN kèm cổng và thời gian phản hồi.

## Chế độ bảo trì và thông báo hệ thống

Bật bảo trì bằng cách đặt `maintenance_mode` thành `true`; đặt nội dung banner vào `system_announcement`. Giao diện web đọc cấu hình công khai `GET /api/v1/admin/settings/public` (không cần đăng nhập, chỉ gồm sáu khóa ở trên), lưu tạm 60 giây.

- Khi đang bảo trì, người dùng thường thấy màn hình "Hệ Thống Đang Bảo Trì" thay cho toàn bộ ứng dụng, có nút "Kiểm tra lại" (đang tải hiện "Đang kiểm tra..."). Giao diện bỏ qua màn hình này nếu danh sách vai trò của người đang đăng nhập có ADMIN, ROLE_ADMIN hoặc MODERATOR. Khách chưa đăng nhập cũng thấy màn hình bảo trì.
- Nếu `system_announcement` không rỗng, banner "Thông Báo Hệ Thống" hiện ở trang chủ. Người dùng bấm nút "Đóng thông báo" (biểu tượng X) thì banner ẩn trong phiên làm việc; đổi nội dung thông báo thì banner hiện lại.
- Khi `allow_registration` là `false`, form đăng ký hiện "Tạm Đóng Đăng Ký".
- Không tải được cấu hình thì giao diện dùng mặc định: không bảo trì, cho đăng ký, 25 MB, không có thông báo. Hạn mức ảnh trên giao diện không thấp hơn 25 MB; video cố định tối đa 150 MB.

Gửi thông báo trong ứng dụng cho người dùng: `POST /api/v1/admin/users/notifications/send` (Admin hoặc Moderator) với `title` và `content` (bắt buộc), `recipientId` (mã người nhận; để trống hoặc ghi `ALL` hay `BROADCAST` để gửi tới mọi người), `type` (mặc định SYSTEM), `targetUrl`. Thao tác ghi nhật ký SEND_NOTIFICATION.

## Nhật ký thao tác quản trị và nhật ký kiểm duyệt

Có hai loại nhật ký:

- **Nhật ký thao tác quản trị** (chỉ Admin): `GET /api/v1/admin/settings/audit-logs` (bảng `audit_logs`) gộp mọi thao tác quản trị với 50 bản ghi kiểm duyệt gần nhất, mới nhất trước. Mỗi dòng có người thực hiện (phần trước dấu @ của email), hành động, loại và mã đối tượng, chi tiết, thời điểm. Cột địa chỉ IP hiện luôn là 127.0.0.1. Các hành động ghi nhận: BAN_USER, UNBAN_USER, CHANGE_ROLE, CREATE_USER, RESET_PASSWORD, REVOKE_SESSIONS, SEND_NOTIFICATION, ADD_BLACKLIST, REMOVE_BLACKLIST, UPDATE_SYSTEM_CONFIG, CLEAR_CACHE, DELETE_GROUP. Chỉ thao tác thành công mới được ghi.
- **Nhật ký kiểm duyệt** (Admin và Moderator): `GET /api/v1/moderation/logs`, phân trang, hiển thị ở mục "Nhật ký & Hoàn tác". Các hành động: Ẩn bài viết, Gỡ bài viết, Gỡ bình luận, Cảnh cáo tác giả, Đề xuất cấm tài khoản, Bỏ qua (Hợp lệ), Khôi phục bài viết, Chấp nhận kháng nghị, Bác bỏ kháng nghị. Quyết định tự động của hệ thống hoặc AI mang mã người xử lý toàn số 0.

## Câu hỏi thường gặp

### Ai được vào trang quản trị của KLTN Social?

Quản trị viên (Admin) và Kiểm duyệt viên (Moderator) vào được "Trung tâm kiểm duyệt" tại `/moderation` hoặc `/admin`. Phần lớn API quản trị (khóa tài khoản, cấu hình, nhóm, nhật ký quản trị) chỉ dành cho Admin; Moderator xem được dashboard, phân tích, từ khóa và xử lý báo cáo. Người dùng thường nhận lỗi 403 "Bạn không có quyền thực hiện thao tác này".

### Kiểm duyệt viên có khóa được tài khoản người dùng không?

Không. Chỉ Admin khóa tài khoản qua `PUT /api/v1/admin/users/{userId}/ban`. Kiểm duyệt viên chỉ chọn "Đề xuất cấm tài khoản", hành động này được ghi vào nhật ký kiểm duyệt chứ không tự khóa ai.

### Khóa tài khoản thì người đó bị đăng xuất ngay không?

Có. Khi khóa, tokenVersion tăng, phiên bản mới được ghi vào Redis (7 ngày) và các thiết bị bị xóa, nên token cũ bị từ chối. Người đó cũng nhận thông báo "ACCOUNT_BANNED". Đăng nhập lại sẽ nhận 403 "Tài khoản của bạn đã bị cấm/khóa do vi phạm tiêu chuẩn cộng đồng."

### Làm sao mở khóa tài khoản đã bị khóa?

Admin gọi `PUT /api/v1/admin/users/{userId}/unban`. Trạng thái trở về ACTIVE, người dùng đăng nhập lại bình thường và hệ thống ghi nhật ký UNBAN_USER.

### Làm sao cấp quyền Kiểm duyệt viên hoặc Admin cho một người dùng?

Admin gọi `PUT /api/v1/admin/users/{userId}/role` với `{"role": "MODERATOR"}` hoặc `{"role": "ADMIN"}`. Người đó cần đăng nhập lại để token mới mang quyền mới. Muốn thu quyền, đặt lại `{"role": "USER"}`.

### Bật chế độ bảo trì như thế nào và ai vẫn dùng được ứng dụng?

Admin gọi `PUT /api/v1/admin/settings/system` với `{"maintenance_mode": "true"}`. Giao diện web sau đó hiện "Hệ Thống Đang Bảo Trì" cho người dùng thường và khách; tài khoản có vai trò ADMIN hoặc MODERATOR vẫn dùng được. Cờ này chỉ chặn ở giao diện web, không chặn các API.

### Tắt đăng ký tài khoản mới có chặn hoàn toàn việc đăng ký không?

Không hoàn toàn. Đặt `allow_registration` là `false` làm form đăng ký trên web hiện "Tạm Đóng Đăng Ký", nhưng API đăng ký phía sau không kiểm tra cờ này.

### Thêm từ khóa cấm có chặn bài viết ngay không?

Danh sách từ khóa cấm ở `/api/v1/admin/settings/blacklist` hiện chỉ lưu và hiển thị. Muốn bộ lọc kiểm duyệt chặn một từ, hãy thêm vào từ khóa nhạy cảm của AI bằng `POST /api/v1/ai/keywords`; từ này có hiệu lực ngay (ACTIVE).

### Từ khóa AI tự học khi nào mới có hiệu lực?

Từ do AI trích xuất ở trạng thái PENDING_REVIEW và chưa dùng để lọc. Admin hoặc Moderator duyệt bằng `POST /api/v1/ai/keywords/{id}/approve` thì từ chuyển sang ACTIVE; nếu là từ vô hại, chọn `/whitelist` để đưa vào danh sách an toàn.

### Xem ai đã làm gì trong hệ thống ở đâu?

Admin xem nhật ký thao tác quản trị bằng `GET /api/v1/admin/settings/audit-logs`. Nhật ký xử lý báo cáo, ẩn hoặc gỡ bài xem ở mục "Nhật ký & Hoàn tác" (`GET /api/v1/moderation/logs`), Admin và Moderator đều xem được.

### Số liệu trên dashboard có chính xác tuyệt đối không?

Số người dùng và báo cáo được đếm trực tiếp, nhưng số bài viết trong ngày, biểu đồ bài đăng và ma trận hoạt động chỉ tính trên các bài mà post-service trả về (tối đa 100 bài mỗi lần), nên là số ước lượng khi hệ thống có nhiều bài. Nếu một dịch vụ lỗi thì chỉ số tương ứng hiện 0.

### Giải tán một nhóm vi phạm như thế nào?

Admin gọi `DELETE /api/v1/admin/groups/{groupId}`. Nhóm bị đánh dấu đã xóa, hệ thống phát sự kiện `group.deleted` và ghi nhật ký DELETE_GROUP.
