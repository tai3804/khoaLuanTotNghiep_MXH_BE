---
title: Kiến trúc kỹ thuật
slug: kien-truc-ky-thuat
audience: developer
tags: [kiến trúc, microservice, cổng, port, api gateway, eureka, jwt, redis, kafka, postgresql, pgvector, embedding, hnsw, rate limit, websocket, s3, build, maven, jdk 21, hikari, spring boot]
updated: 2026-11-19
---

# Kiến trúc kỹ thuật

Tài liệu này dành cho lập trình viên và được viết theo mã nguồn hiện tại của backend KLTN Social (các file README, PROJECT_STRUCTURE cũ còn nhắc MySQL, MongoDB, Spring Boot 3 và cổng sai là đã lỗi thời). Nội dung gồm danh sách dịch vụ và cổng, đường đi của một request, xác thực, Kafka, cơ sở dữ liệu, các thuật toán tìm kiếm, bảng tin, gợi ý, các luồng kiểm duyệt và điểm uy tín, WebSocket, kho tệp, cấu hình môi trường, cách build và chạy. Không có giá trị bí mật nào được ghi ở đây.

## Tổng quan kiến trúc và công nghệ

KLTN Social là hệ thống microservice viết bằng Java 21 với Spring Boot 4.1.0 và Spring Cloud 2025.1.2 (Eureka, Gateway, OpenFeign). Dữ liệu nằm trong PostgreSQL có tiện ích pgvector, sự kiện đi qua Apache Kafka, bộ nhớ đệm và trạng thái ngắn hạn nằm trong Redis, tệp ảnh và video lưu ở AWS S3, mô hình AI là Google Gemini. Giao diện web dùng React 19, TypeScript và Vite, nói chuyện với backend qua API Gateway và qua STOMP WebSocket. Hệ thống không dùng MySQL hay MongoDB.

Cách tổ chức mã:
- Không có file pom gốc: mỗi dịch vụ là một dự án Maven riêng (có sẵn `mvnw`).
- `common-framework` là thư viện dùng chung (bảo mật JWT, xử lý lỗi, Redis, lớp sự kiện Kafka, nạp `.env`); mọi dịch vụ trừ `api-gateway` và `eureka-server` phụ thuộc vào nó.
- Thư mục `moderation-service` không có mã nguồn; chức năng báo cáo và kiểm duyệt nằm trong `admin-service`.
- Các dịch vụ gọi nhau bằng OpenFeign (HTTP) và bằng sự kiện Kafka; tìm dịch vụ qua Eureka.

## Danh sách dịch vụ, cổng và vai trò từng dịch vụ

Cổng mặc định đọc từ `server.port` trong `application.yaml` của từng dịch vụ và đổi được bằng biến `SERVER_PORT`.

| Dịch vụ | Cổng | Vai trò |
|---|---|---|
| eureka-server | 8761 | Sổ đăng ký dịch vụ (Eureka) |
| api-gateway | 8080 | Cổng vào duy nhất: định tuyến, CORS, giới hạn tần suất |
| auth-service | 8081 | Đăng ký, đăng nhập, OTP, MFA, thiết bị, ký JWT, API nội bộ cho quản trị, tạo tài khoản Admin lần đầu |
| post-service | 8082 | Bài viết, bình luận, cảm xúc, tin (story), tìm kiếm, bảng tin xếp hạng, vector bài viết, kháng nghị bài |
| notification-service | 8083 | Thông báo trong ứng dụng (WebSocket), email Brevo, đẩy FCM (tắt mặc định) |
| media-service | 8084 | Tải tệp lên S3, hạn mức 1 GB mỗi người dùng |
| user-service | 8085 | Hồ sơ, bạn bè, theo dõi, chặn, nhóm, điểm uy tín, gợi ý kết bạn và nhóm |
| call-service | 8086 | Tín hiệu cuộc gọi và phát trực tiếp (live) |
| chat-service | 8087 | Nhắn tin, trạng thái trực tuyến |
| feed-service | 8088 | Bảng `user_feed_items` (phân phát bài tới người nhận khi có bài mới) |
| admin-service | 8090 | Báo cáo, kiểm duyệt, thống kê, cấu hình hệ thống, nhật ký quản trị |
| ai-service | 8091 | Gemini: kiểm duyệt, tóm tắt, trợ lý, vector embedding, kho tri thức RAG |

Giao diện web lấy bảng tin xếp hạng từ post-service chứ không gọi `/api/v1/feeds` của feed-service.

## Một request đi qua API Gateway như thế nào

Client chỉ gọi api-gateway (cổng 8080). Gateway định tuyến theo đường dẫn tới dịch vụ qua Eureka (`lb://tên-dịch-vụ`), hoặc tới địa chỉ cố định nếu đặt biến `*_SERVICE_URL`:

- `/api/v1/auth/**`, `/api/v1/devices/**` tới auth-service.
- `/api/v1/users/**`, `/api/v1/groups/**` tới user-service.
- `/api/v1/admin/**`, `/api/v1/moderation/**` tới admin-service.
- `/api/v1/posts/**`, `/api/v1/stories/**` tới post-service; `/api/v1/feeds/**` tới feed-service.
- `/api/v1/notifications/**`, `/ws-notifications` tới notification-service; `/api/v1/media/**` tới media-service.
- `/api/v1/chat/**`, `/ws-chat` tới chat-service; `/api/v1/calls/**`, `/api/v1/call/**`, `/ws-call` tới call-service.
- `/api/v1/ai/**` tới ai-service.

Gateway còn: chặn `/api/v1/internal/**` và `/api/v1/ai/embeddings` từ bên ngoài (HTTP 403 "Truy cập đường dẫn nội bộ bị từ chối"), xóa header `X-Internal-Key` do client gửi, gắn mã truy vết `X-Correlation-Id`, thêm các header bảo mật chuẩn. Gateway không giải mã JWT; từng dịch vụ tự xác thực token. CORS cấu hình bằng `CORS_ALLOWED_ORIGINS` (danh sách phân tách bằng dấu phẩy), cho phép GET, POST, PUT, PATCH, DELETE, OPTIONS, cho gửi thông tin xác thực và lộ các header `Retry-After`, `X-RateLimit-*`.

## Giới hạn tần suất (rate limiting) và các biến RATE_LIMIT_*

Gateway dùng thuật toán token bucket trong bộ nhớ của tiến trình gateway (không dùng Redis), chia bốn nhóm. Mỗi request tốn 1 token; hết token trả HTTP 429. Giá trị mặc định:

| Nhóm | Áp dụng cho | Dung lượng | Nạp 1 token mỗi |
|---|---|---|---|
| AUTH | `/api/v1/auth/login`, `/register`, `/forgot-password`, `/mfa` | 5 | 30 giây |
| AI | `/api/v1/ai` | 5 | 10 giây |
| MUTATION | POST/PUT/PATCH/DELETE tới `/api/v1/posts`, `/media`, `/groups`, `/comments` | 10 | 30 giây |
| GENERAL | mọi request còn lại | 300 | khoảng 30 token mỗi giây |

Biến cấu hình: `RATE_LIMIT_ENABLED` (true/false), `RATE_LIMIT_AUTH_CAPACITY`, `RATE_LIMIT_AUTH_REFILL_SECONDS`, `RATE_LIMIT_AI_CAPACITY`, `RATE_LIMIT_AI_REFILL_SECONDS`, `RATE_LIMIT_MUTATION_CAPACITY`, `RATE_LIMIT_MUTATION_REFILL_SECONDS`, `RATE_LIMIT_GENERAL_CAPACITY`, `RATE_LIMIT_GENERAL_REFILL_SECONDS`. Nhóm AUTH chỉ tính theo IP; các nhóm khác tính theo dấu vân tay của token, kèm bucket chặn theo IP có hạn mức gấp 5 lần. IP thật lấy từ `X-Forwarded-For` theo thuộc tính `rate-limit.trusted-proxy-hops` (mặc định 1, đặt 0 để bỏ qua). Request OPTIONS, WebSocket và `/actuator` không bị giới hạn.

Khi vượt hạn mức, client nhận HTTP 429 cùng các header `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` và nội dung "Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ N giây trước khi thử lại." Lưu ý: `.env.example` còn liệt kê tên cũ `RATE_LIMIT_*_PER_MINUTE` và `RATE_LIMIT_RETRY_AFTER_SECONDS`; gateway không đọc các tên này.

## Xác thực JWT RS256 và phân quyền

auth-service ký JWT bằng thuật toán RS256 với khóa bí mật `JWT_PRIVATE_KEY`; mọi dịch vụ khác xác thực bằng khóa công khai `JWT_PUBLIC_KEY` (cấu hình trong `common-framework`). Access token sống 15 phút (`JWT_ACCESS_TOKEN_EXPIRATION`, 900000 ms), refresh token sống 30 ngày (`JWT_REFRESH_TOKEN_EXPIRATION`). Access token chứa các claim: `sub` (mã người dùng), `email`, `firstName`, `lastName`, `middleName`, `roles`, `permissions`, `tokenVersion`.

Phân quyền dựa trên claim `roles` (ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN; hỗ trợ cả có và không có tiền tố ROLE_) với `@PreAuthorize`. Token sai hoặc hết hạn trả HTTP 401: "Phiên đăng nhập đã hết hạn hoặc chưa xác thực". Thiếu quyền trả HTTP 403: "Bạn không có quyền thực hiện thao tác này".

API nội bộ `/api/v1/internal/**` (dịch vụ gọi dịch vụ, không qua gateway) yêu cầu header `X-Internal-Key` khớp biến `INTERNAL_API_KEY`; các lời gọi Feign tự gắn header này. Nếu chạy ở profile `prod` hoặc đặt `REQUIRE_STRONG_SECRETS=true`, dịch vụ từ chối khởi động khi khóa nội bộ hoặc mật khẩu Admin mặc định còn là giá trị mặc định của môi trường dev.

## Thu hồi token bằng Redis và hành vi khi Redis tắt

Mỗi người dùng có một số phiên bản token (`tokenVersion`). Khi khóa tài khoản, đổi vai trò, đặt lại hoặc đổi mật khẩu, thu hồi phiên, auth-service tăng số này trong cơ sở dữ liệu và ghi vào Redis với khóa `auth-service:user:token_version:{userId}`, giữ 7 ngày. Mỗi dịch vụ, khi giải mã JWT, so `tokenVersion` trong token với giá trị trong Redis; token có số nhỏ hơn bị coi là đã thu hồi và nhận HTTP 401. Kiểm tra này cũng áp dụng cho khung STOMP của WebSocket.

Khi Redis không truy cập được (theo mã `common-framework` hiện tại, mặc định đóng an toàn):
1. Nếu dịch vụ đã đọc được phiên bản của người dùng đó trong 15 phút gần nhất thì dùng giá trị đã nhớ.
2. Nếu chưa có dữ liệu gần đây thì token bị từ chối (fail-closed), kể cả token còn hợp lệ.

Đặt `app.security.token-revocation.fail-open=true` để bỏ qua kiểm tra khi Redis lỗi (kém an toàn hơn). Dịch vụ phải chạy bản `common-framework` đã được `mvn install` mới có hành vi này.

## Redis dùng để làm gì

Redis (cổng 6379, cấu hình `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED`) được dùng cho:
- Thu hồi token: khóa `auth-service:user:token_version:{userId}` (xem mục trên).
- Mã OTP và phiên tạm của auth-service: OTP đăng ký, OTP quên mật khẩu, phiên và OTP của MFA, bộ đếm đăng nhập sai (`auth:login_failed:`; mặc định khóa tạm 15 phút sau 5 lần sai, đổi bằng `AUTH_MAX_FAILED_LOGIN_ATTEMPTS`, `AUTH_FAILED_LOGIN_LOCKOUT_MINUTES`).
- Bộ nhớ đệm Spring Cache của post-service (mặc định sống 30 giây) và bản chụp bảng tin đã xếp hạng (15 phút); Redis lỗi thì tính lại từ cơ sở dữ liệu, không làm lỗi request.
- Bộ nhớ đệm gợi ý kết bạn của user-service (3 phút).
- Trạng thái trực tuyến của người dùng do chat-service ghi.
- Admin có thể dọn các nhóm khóa này bằng `POST /api/v1/admin/settings/clear-cache`.

Giới hạn tần suất của gateway không dùng Redis.

## Các topic Kafka chính, ai phát và ai nhận

Kafka chạy ở cổng 9092. Các topic có trong mã nguồn:

| Topic | Bên phát | Bên nhận |
|---|---|---|
| post.created | post-service | ai-service, feed-service, media-service |
| post.deleted | post-service | feed-service, media-service |
| post.media.uploaded | media-service | post-service |
| post.moderated | admin-service, ai-service | post-service, feed-service |
| post.flagged | ai-service | admin-service |
| post.warned | admin-service | post-service |
| post.summarized | ai-service | post-service |
| post.appeal.reviewed | post-service | admin-service |
| comment.moderated | admin-service | post-service |
| report.created | admin-service | ai-service |
| user.reputation.adjust | post-service | user-service |
| user.registered | auth-service | user-service |
| user.avatar.updated | user-service | media-service |
| group.deleted, group.member-content-removed | user-service | post-service |
| notification.in-app.send | admin, ai, auth, call, chat, post, user | notification-service |
| notification.email.register-otp, notification.email.password-reset | auth-service | notification-service |

Hai ngoại lệ theo mã hiện tại: topic `notification.push.send` có bên nhận ở notification-service nhưng không có bên phát qua Kafka (đẩy FCM đi theo API `/push/send`); topic `notification.email.mfa-otp` do auth-service phát nhưng không tìm thấy `@KafkaListener` nhận. Mỗi bên nhận dùng group id riêng (ví dụ `ai-service-post-group`, `feed-service-group`, `media-group`).

## Cơ sở dữ liệu PostgreSQL, pgvector và các bảng embedding

Cơ sở dữ liệu là PostgreSQL 16 có pgvector (ảnh Docker `pgvector/pgvector:pg16`, cổng 5432). Mỗi dịch vụ có CSDL mặc định riêng: `auth_db`, `user_db`, `post_db`, `media_db`, `chat_db`, `call_db`, `notification_db`, `feed_db`, `admin_db`, `ai_db`; địa chỉ lấy từ `SPRING_DATASOURCE_URL`. Nếu mọi dịch vụ dùng chung một địa chỉ thì các bảng cùng nằm trong một CSDL. Lược đồ do Hibernate tự cập nhật (`JPA_DDL_AUTO` mặc định `update`).

Vector embedding 768 chiều do Gemini tạo (đã chuẩn hóa L2), lưu trong các bảng dùng chỉ mục HNSW với độ đo cosine (`vector_cosine_ops`):
- `post_embeddings` (post_id, embedding, updated_at) và `user_search_interests` (user_id, query_text, embedding, updated_at): post-service.
- `user_embeddings` và `group_embeddings`: user-service.
- `knowledge_chunks` (chunk_key, doc_slug, doc_title, heading, audience, tags, ord, content, content_hash, embedding, updated_at): ai-service, kho tri thức của trợ lý AI.

Các dịch vụ tự chạy `CREATE EXTENSION IF NOT EXISTS vector` và tạo bảng khi khởi động. Mọi vector trong cùng một bảng phải do cùng một model tạo ra: ghim model bằng `GEMINI_EMBEDDING_MODEL`, và đổi model thì phải nạp lại toàn bộ embedding.

## Thuật toán tìm kiếm bài viết

Tìm kiếm bài viết ở post-service theo ngữ nghĩa, không dùng tìm kiếm toàn văn hay BM25 (mã khởi động còn gỡ cột và chỉ mục tsvector cũ). Các bước:
1. Tạo vector cho truy vấn (qua ai-service, có cache 30 phút) và đo phân phối cosine nền của chính truy vấn đó trên khoảng 400 bài lấy mẫu; điểm ngữ nghĩa tính theo z-score so với nền này.
2. Truy xuất ứng viên song song: (A) ANN HNSW cosine toàn cục, tối đa 150 bài, z từ 2 trở lên; (B) đồ thị xã hội: cosine chính xác trên tối đa 300 bài trong 90 ngày của bạn bè, người đang theo dõi và chính mình, giữ tối đa 100; (C) hashtag có trong truy vấn, tối đa 50 bài mỗi hashtag.
3. Hợp nhất bằng Reciprocal Rank Fusion (k = 60), giữ 120 ứng viên, rồi lọc trạng thái và quyền xem trước khi xếp hạng.
4. Xếp hạng lại bằng điểm kết hợp ngữ nghĩa, cụm từ khớp, hashtag, điều chỉnh theo tín hiệu xã hội, tương tác, độ mới và uy tín tác giả; bộ trọng số có thể đặt qua biến `SEARCH_WEIGHTS`.
5. Cắt đuôi tương đối 0,35, đa dạng hóa bằng MMR (λ = 0,7) và trả tối đa 200 kết quả.

Không tạo được vector cho truy vấn (ai-service lỗi) thì chỉ còn nhánh hashtag.

## Bảng tin xếp hạng

Bảng tin do `FeedRankingService` của post-service tính, có ba chế độ tương ứng ba tab "Tất cả" (relevance), "Mới nhất" (recent) và "Nổi bật" (trending). Với chế độ relevance: lấy tối đa 400 bài ứng viên (cửa sổ 14, 60 rồi 365 ngày nếu ít hơn 40 bài), lọc quyền xem, chấm điểm, đa dạng hóa bằng MMR trên 120 bài đầu và giữ tối đa 200 bài trong một bản chụp ở Redis (15 phút) để phân trang ổn định.

Điểm gồm độ thân thiết với tác giả (bạn bè 3,0, đang theo dõi 2,0, bài của mình 0,5, nhóm đã tham gia 1,5, lịch sử tương tác), tương tác theo logarit, độ mới giảm theo nửa đời 24 giờ, cộng tín hiệu từ chủ đề vừa tìm kiếm, lọc cộng tác và hồ sơ sở thích, rồi nhân trọng số uy tín của tác giả. Chế độ trending dùng tương tác chia cho (tuổi giờ + 2) lũy thừa 1,3.

## Gợi ý kết bạn

Gợi ý kết bạn do user-service tính (`FriendSuggestionEngine`) trên đồ thị ego bán kính 3. Ứng viên đến từ bạn của bạn, thành viên nhóm chung, người theo dõi qua lại và người ở bậc 3. Điểm kết hợp Adamic-Adar cho bạn chung, Personalized PageRank (xác suất quay về 0,15, tối đa 50 vòng lặp) cho quan hệ gián tiếp, và Label Propagation để nhận ra cùng cộng đồng. Loại chính mình, bạn hiện tại, người bị chặn và các lời mời đang chờ hoặc đã bị từ chối. Kết quả lưu đệm trong Redis 3 phút.

## Luồng kiểm duyệt bài viết mới bằng AI

Khi post-service xuất bản bài, nó phát `post.created`. ai-service nhận và gọi `evaluateNewPost`:
1. Bỏ qua nếu cấu hình `ai_moderation_enabled` (admin-service) hoặc `AI_MODERATION_ENABLED` đang tắt, hoặc bài đã được chấm trước đó.
2. Chấm bằng bộ lọc từ khóa nhạy cảm (Aho-Corasick) kết hợp Gemini. Bài bị gắn cờ khi điểm độc hại từ 0,40 (`AI_TOXICITY_THRESHOLD_WARN`) trở lên hoặc AI đề xuất ẩn, cảnh cáo hay gỡ.
3. Bài bị gắn cờ: ai-service phát `post.moderated` (HIDE_POST, lý do bắt đầu bằng "AI_AUTO_MODERATION") để post-service tạm ẩn bài, phát `post.flagged` để admin-service đưa vào hàng chờ của kiểm duyệt viên (báo cáo nguồn AI, trạng thái PENDING), và gửi thông báo "Bài viết của bạn đang chờ kiểm duyệt" cho tác giả.
4. Bài an toàn và đủ dài (từ `AI_SUMMARY_MIN_LENGTH`, mặc định 1000 ký tự) được tóm tắt rồi phát `post.summarized`.

Với báo cáo của người dùng, admin-service phát `report.created`; ai-service chấm lại bài bị báo cáo (ngưỡng ẩn 0,65, gỡ 0,85) và xử lý báo cáo qua API nội bộ của admin-service.

## Luồng điểm uy tín

Quyết định kiểm duyệt đi từ `post.moderated` tới điểm uy tín như sau: post-service nhận `post.moderated`, ẩn hoặc gỡ bài, rồi phát `user.reputation.adjust` (loại PENALTY với hành động AUTO_HIDE, HIDE_POST hoặc DELETE_POST; loại REVERT khi bài được khôi phục hoặc kháng nghị được chấp nhận). Hành động WARN_USER (cảnh cáo) đến từ topic `post.warned` do admin-service phát và cũng được post-service chuyển thành PENALTY. user-service nhận và áp dụng qua `ReputationService`. Quyết định do AI tự tạm ẩn (lý do chứa AI_AUTO_MODERATION) chưa trừ điểm.

Tham số trong `user-service/application.yaml`: điểm khởi đầu 100; trừ 5 khi cảnh cáo, 5 khi bài tự ẩn do nhiều báo cáo, 10 khi kiểm duyệt viên ẩn bài, 10 khi gỡ bài; hồi 1 điểm mỗi ngày không bị trừ; điểm dưới 10 thì khóa tài khoản 30 ngày, hết hạn khóa đặt lại 30 điểm. Hạn chế theo bậc điểm được post-service thực thi.

## WebSocket cho chat, thông báo và cuộc gọi

Có ba điểm cuối STOMP, mỗi điểm hỗ trợ cả SockJS và WebSocket thuần: `/ws-chat` (chat-service), `/ws-notifications` (notification-service), `/ws-call` (call-service). Gateway định tuyến và không giới hạn tần suất các đường dẫn này. Broker là broker đơn giản trong bộ nhớ của từng dịch vụ với tiền tố `/topic`, `/queue`, `/app` (gửi lên) và `/user`.

Xác thực khi gói STOMP CONNECT: gửi JWT trong header `Authorization: Bearer ...` hoặc header `access_token`; không có token hoặc token sai, hết hạn, đã thu hồi thì bị từ chối. Origin được phép lấy từ `CORS_ALLOWED_ORIGINS`. Một số đích: chat gửi tới `/app/chat.sendMessage/{conversationId}` và nhận ở `/topic/conversations/{id}`; thông báo nhận ở `/topic/notifications.{userId}` và `/topic/notifications.count.{userId}`; cuộc gọi dùng `/app/call.signal/{callSessionId}` và `/topic/call/{sessionId}`; trạng thái trực tuyến ở `/topic/presence`.

## Kho tệp media-service và AWS S3

media-service chỉ lưu tệp trên AWS S3 (không có lưu trữ cục bộ). Cấu hình qua `AWS_REGION` (mặc định ap-southeast-1), `AWS_S3_BUCKET_NAME`, `AWS_S3_CUSTOM_DOMAIN`, `AWS_S3_MAX_FILE_SIZE_BYTES` (mặc định 157286400, tức 150 MB), cùng cặp khóa truy cập AWS hoặc chuỗi thông tin xác thực mặc định của AWS. Tệp được phát lại qua `/api/v1/media/files/**` (công khai) tại địa chỉ `MEDIA_PUBLIC_BASE_URL`. Các API chính: tải lên một hoặc nhiều tệp, lấy presigned URL, xem hạn mức (1 GB mỗi người dùng), thư viện của người dùng, xóa. Giới hạn multipart là 150 MB mỗi tệp và 160 MB mỗi request. media-service còn nhận `post.created`, `post.deleted` và `user.avatar.updated` để xử lý tệp và phát `post.media.uploaded`.

## Cấu hình môi trường và file .env

`common-framework` có `DotenvEnvironmentPostProcessor` tự nạp file `.env` khi dịch vụ khởi động, tìm ở thư mục làm việc rồi tới thư mục cha (nên chạy từ thư mục module vẫn thấy `.env` ở gốc backend); api-gateway có bản nạp riêng, eureka-server không nạp. Biến hệ điều hành được ưu tiên hơn `.env`. File `.env` bị gitignore; `.env.example` là mẫu (chưa cập nhật tên biến RATE_LIMIT_*).

Các nhóm biến quan trọng (không ghi giá trị bí mật):
- Hạ tầng: `EUREKA_SERVER_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_*`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JPA_DDL_AUTO`.
- Bảo mật: `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY` (chỉ auth-service), `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION`, `INTERNAL_API_KEY`, `REQUIRE_STRONG_SECRETS`, `ADMIN_DEFAULT_PASSWORD`, `CORS_ALLOWED_ORIGINS`.
- Địa chỉ trực tiếp giữa dịch vụ (khi không dùng Eureka): `AUTH_SERVICE_URL`, `USER_SERVICE_URL`, `POST_SERVICE_URL`, `ADMIN_SERVICE_URL`, `AI_SERVICE_URL`, ...
- Dịch vụ ngoài: `AWS_*`, `BREVO_*`, `FCM_ENABLED`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_EMBEDDING_MODEL`, `AI_SUMMARY_ENABLED`, `AI_MODERATION_ENABLED`, `AI_AUTO_LEARN_ENABLED`, `AI_TOXICITY_THRESHOLD_*`.
- Giới hạn tần suất: `RATE_LIMIT_*`; khóa đăng nhập sai: `AUTH_MAX_FAILED_LOGIN_ATTEMPTS`, `AUTH_FAILED_LOGIN_LOCKOUT_MINUTES`; mã OTP: `AUTH_OTP_COOLDOWN_SECONDS`.
- Kết nối CSDL: `HIKARI_MAX_POOL_SIZE`, `HIKARI_MIN_IDLE`.

## Kích thước pool kết nối cơ sở dữ liệu

`application-common.yaml` của `common-framework` đặt `spring.datasource.hikari.maximum-pool-size` bằng `${HIKARI_MAX_POOL_SIZE:2}` và `minimum-idle` bằng `${HIKARI_MIN_IDLE:1}`, nên mặc định mỗi dịch vụ chỉ có 2 kết nối. File chung này được nhập vào và ghi đè `application.yaml` của dịch vụ, vì vậy khai báo `spring.datasource.hikari.maximum-pool-size` trong `application.yaml` của dịch vụ không có tác dụng. Cách đặt đúng là khóa gốc `HIKARI_MAX_POOL_SIZE` trong `application.yaml` (hoặc biến môi trường cùng tên, vẫn ghi đè được): post-service đặt 8, user-service đặt 6.

Lý do: post-service và user-service vừa dùng JPA vừa dùng JdbcTemplate (tìm kiếm vector) trong cùng một request, tức một request cần hai kết nối cùng lúc; với pool 2 thì hai request đồng thời đã có thể kẹt đến hết timeout 30 giây. Cơ sở dữ liệu đám mây dùng chung có giới hạn khoảng 100 kết nối cho toàn bộ dịch vụ nên không nên đặt quá lớn.

## Cách build, chạy và kiểm thử

Yêu cầu JDK 21. Các bước:
1. Chạy hạ tầng bằng `docker compose up -d` ở thư mục gốc backend: PostgreSQL pgvector (5432), Redis (6379), RedisInsight (5540), Kafka chế độ KRaft (9092), Kafka UI (8089) và Zipkin (9411). File này chỉ chứa hạ tầng, không chứa các dịch vụ ứng dụng.
2. Build `common-framework` trước: `mvn install -DskipTests` trong thư mục đó (hoặc `./mvnw -f common-framework/pom.xml clean install -DskipTests`). Sửa `common-framework` xong phải `mvn install` lại rồi khởi động lại các dịch vụ phụ thuộc (admin, ai, auth, call, chat, feed, media, notification, post, user).
3. Chạy từng module bằng `mvn spring-boot:run` hoặc `mvn package` rồi `java -jar target/*.jar`, nên đứng ở thư mục module. Thứ tự gợi ý: eureka-server, api-gateway, rồi các dịch vụ còn lại; gateway chỉ định tuyến được tới dịch vụ đã đăng ký Eureka.

Mỗi dịch vụ có `Dockerfile` (build bằng JDK 21, chạy bằng JRE 21, ngữ cảnh build là thư mục gốc backend vì cần `common-framework`). GitHub Actions (`ci-matrix-all.yml`) build `common-framework` rồi từng dịch vụ.

Kiểm thử là JUnit, chạy bằng `mvn test` trong từng module. Các bộ test có nội dung nằm ở: common-framework (thu hồi token, cấu hình bảo mật), api-gateway (giới hạn tần suất), post-service (xếp hạng bảng tin, MMR, lọc cộng tác, hồ sơ sở thích, pipeline và đánh giá tìm kiếm, gom cụm chủ đề), user-service (đồ thị, điểm gợi ý kết bạn), ai-service (Aho-Corasick, bộ lọc từ khóa, cache embedding, nạp kho tri thức, RAG), admin-service (phân tích hashtag). Các dịch vụ còn lại chỉ có test nạp ngữ cảnh Spring; auth-service chưa có test. Giao diện web chưa có script kiểm thử, chỉ có `typecheck` và `lint`.

## Câu hỏi thường gặp

### Hệ thống dùng cơ sở dữ liệu gì, có dùng MySQL hay MongoDB không?

Không. Toàn bộ dữ liệu nằm trong PostgreSQL 16 có tiện ích pgvector (kể cả chat-service). Redis chỉ giữ bộ nhớ đệm và trạng thái ngắn hạn; các tài liệu cũ nhắc MySQL hoặc MongoDB đã lỗi thời.

### Dịch vụ nào chạy ở cổng nào?

api-gateway 8080, auth-service 8081, post-service 8082, notification-service 8083, media-service 8084, user-service 8085, call-service 8086, chat-service 8087, feed-service 8088, admin-service 8090, ai-service 8091, eureka-server 8761. Đổi bằng biến `SERVER_PORT` của từng dịch vụ.

### Phiên bản Spring Boot và Java của dự án là gì?

Java 21 và Spring Boot 4.1.0 (Spring Cloud 2025.1.2), không phải Spring Boot 3. Cần JDK 21 để build.

### Vì sao tôi nhận lỗi 429 "Bạn đang gửi yêu cầu quá nhanh"?

Gateway giới hạn tần suất theo nhóm AUTH, AI, MUTATION, GENERAL. Mặc định nhóm AUTH chỉ 5 yêu cầu rồi nạp 1 mỗi 30 giây, nhóm AI 5 và 1 mỗi 10 giây, nhóm MUTATION 10 và 1 mỗi 30 giây, nhóm GENERAL 300. Chờ đúng số giây trong `Retry-After`, hoặc chỉnh các biến `RATE_LIMIT_*_CAPACITY` và `RATE_LIMIT_*_REFILL_SECONDS` rồi khởi động lại gateway; `RATE_LIMIT_ENABLED=false` tắt hẳn.

### Redis tắt thì điều gì xảy ra với đăng nhập?

Mặc định kiểm tra thu hồi token là đóng an toàn: nếu Redis lỗi và dịch vụ chưa đọc được phiên bản token của người dùng đó trong 15 phút gần nhất thì token bị từ chối (HTTP 401). Nếu có giá trị đã đọc trong 15 phút gần nhất thì dùng giá trị đó. Đặt `app.security.token-revocation.fail-open=true` để bỏ qua kiểm tra khi Redis lỗi.

### Khóa tài khoản làm token cũ mất hiệu lực bằng cách nào?

auth-service tăng `tokenVersion` của người dùng và ghi vào Redis (khóa `auth-service:user:token_version:{userId}`, giữ 7 ngày). Mọi dịch vụ so `tokenVersion` trong JWT với giá trị này nên token cũ bị từ chối ngay, không cần chờ hết hạn.

### Làm sao build và chạy backend trên máy mình?

Dùng JDK 21, chạy `docker compose up -d` cho hạ tầng, `mvn install -DskipTests` trong `common-framework` trước, rồi chạy từng module (`mvn spring-boot:run`) bắt đầu từ eureka-server và api-gateway. Không có pom gốc nên mỗi module build riêng; sau khi cài lại `common-framework` phải khởi động lại các dịch vụ phụ thuộc.

### Ảnh và video được lưu ở đâu?

Trên AWS S3 do media-service quản lý; không có lưu trữ cục bộ. Client xem tệp qua `/api/v1/media/files/**`. Mỗi tệp tối đa 150 MB và mỗi người dùng có hạn mức 1 GB.

### Tìm kiếm bài viết có dùng full-text hay BM25 không?

Không. Tìm kiếm dùng embedding 768 chiều với chỉ mục HNSW cosine, kết hợp đồ thị xã hội và hashtag, hợp nhất bằng RRF, xếp hạng lại rồi đa dạng hóa bằng MMR.

### Bài viết mới được kiểm duyệt như thế nào, đi qua topic Kafka nào?

post-service phát `post.created`; ai-service chấm. Bài nghi vấn bị tạm ẩn qua `post.moderated` và được đưa vào hàng chờ kiểm duyệt qua `post.flagged` (admin-service nhận). Quyết định của kiểm duyệt viên đi tiếp qua `post.moderated` rồi `user.reputation.adjust` để trừ điểm uy tín ở user-service.

### Vì sao pool kết nối cơ sở dữ liệu mặc định chỉ có 2?

`application-common.yaml` đọc `HIKARI_MAX_POOL_SIZE` với mặc định 2. post-service đặt 8 và user-service đặt 6 bằng khóa gốc `HIKARI_MAX_POOL_SIZE` trong `application.yaml` của chúng, vì hai dịch vụ này cần hai kết nối cho một request (JPA và JdbcTemplate). Khai báo `maximum-pool-size` trong `application.yaml` của dịch vụ không có tác dụng.

### WebSocket được dùng ở những đâu và xác thực thế nào?

Ba điểm cuối STOMP: `/ws-chat`, `/ws-notifications`, `/ws-call`. Khách gửi JWT trong header `Authorization` hoặc `access_token` của gói CONNECT; token sai, hết hạn hoặc đã thu hồi bị từ chối.
