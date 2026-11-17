# TÀI LIỆU KỸ THUẬT: VECTOR EMBEDDING VÀ CÁC GIẢI THUẬT TÌM KIẾM, ĐỀ XUẤT, KIỂM DUYỆT (KLTN SOCIAL)

> **Phiên bản:** đồng bộ với mã nguồn và báo cáo `KhoaLuan_MangXaHoi_v21.docx`.
> **Nguyên tắc của tài liệu:** mọi công thức và hằng số dưới đây được đối chiếu trực tiếp với mã nguồn (đường dẫn ghi ở từng mục).
> Chỗ nào là số đo thực nghiệm chưa đo lại sau đợt tối ưu gần nhất thì ghi rõ.

**Công nghệ lõi:** Java 21, Spring Boot 3.x, Google Gemini (embedding 768 chiều + sinh văn bản), PostgreSQL + `pgvector` (HNSW), Redis, Apache Kafka.

---

## MỤC LỤC

1. [Bảng tổng hợp các giải thuật](#1-bảng-tổng-hợp-các-giải-thuật)
2. [Vector embedding và chỉ mục HNSW](#2-vector-embedding-và-chỉ-mục-hnsw)
3. [Tìm kiếm lai nhiều tầng](#3-tìm-kiếm-lai-nhiều-tầng)
4. [Xếp hạng bảng tin](#4-xếp-hạng-bảng-tin)
5. [Đề xuất: chủ đề vừa tìm, hồ sơ sở thích, lọc cộng tác](#5-đề-xuất-chủ-đề-vừa-tìm-hồ-sơ-sở-thích-lọc-cộng-tác)
6. [Thịnh hành và hashtag đang nổi](#6-thịnh-hành-và-hashtag-đang-nổi)
7. [Gợi ý kết bạn](#7-gợi-ý-kết-bạn)
8. [Kiểm duyệt nội dung ba tầng (Aho–Corasick)](#8-kiểm-duyệt-nội-dung-ba-tầng-aho–corasick)
9. [Giới hạn tốc độ Token Bucket tại API Gateway](#9-giới-hạn-tốc-độ-token-bucket-tại-api-gateway)
10. [Nhật ký tối ưu và sửa lỗi đợt này](#10-nhật-ký-tối-ưu-và-sửa-lỗi-đợt-này)
11. [Các giải thuật có thể áp dụng thêm](#11-các-giải-thuật-có-thể-áp-dụng-thêm)
12. [Về số liệu hiệu năng](#12-về-số-liệu-hiệu-năng)

---

## 1. Bảng tổng hợp các giải thuật

| Bài toán | Giải thuật | Độ phức tạp chính | Mã nguồn |
|:---|:---|:---|:---|
| Tìm vector gần nhất | HNSW (ANN) trên pgvector, cosine | ≈ O(log N) | `PostEmbeddingService.searchSimilar` |
| Tìm kiếm bài viết | Lai: BM25 + vector + đồ thị xã hội, hợp nhất RRF | O(K log K) trên K ≤ 450 ứng viên | `search/PostSearchService`, `RankFusion`, `Bm25`, `SearchScorer` |
| Xếp hạng bảng tin | Điểm đa nhân tố: Affinity × Engagement(log) × Freshness(bán rã mũ) + đa dạng hóa tác giả | O(V + R) và O(L·P) cho đa dạng hóa | `ranking/PostRelevanceScorer`, `FeedRankingService` |
| Đề xuất theo hành vi | Lọc cộng tác theo bài viết (Item-based CF), cosine có co (shrinkage) | O(số cặp đồng xuất hiện) | `ranking/ItemCollaborativeFilter` |
| Đề xuất theo nội dung | Hồ sơ sở thích (centroid kiểu Rocchio) | O(M·D), M ≤ 50, D = 768 | `ranking/UserInterestProfile` |
| Thịnh hành | Suy giảm trọng lực kiểu Hacker News | O(1) mỗi bài | `PostRelevanceScorer.trending` |
| Hashtag nổi | So hai cửa sổ liền kề, z-score xấp xỉ Poisson | O(số bài) | `admin-service/HashtagTrendAnalyzer` |
| Gợi ý kết bạn | Adamic–Adar + tín hiệu nhóm/theo dõi/hồ sơ | O(bậc 2 của đồ thị) | `user-service/.../suggestion/*` |
| Lọc từ khóa độc hại | Aho–Corasick, khớp theo ranh giới từ | O(N + số lần khớp) | `ai-service/.../engine/AhoCorasickMatcher` |
| Chống lạm dụng API | Token Bucket phân tầng + bucket chặn theo IP | O(1) mỗi yêu cầu | `api-gateway/.../RateLimitingGlobalFilter` |
| Trợ lý hỏi đáp | RAG dùng chính đường ống tìm kiếm, tôn trọng quyền xem | — | `ai-service/.../RagAssistantService` |

---

## 2. Vector embedding và chỉ mục HNSW

### 2.1. Sinh vector

`ai-service` gọi Gemini `embedContent` với `outputDimensionality = 768`. Hai điểm quan trọng đã được xử lý trong đợt này:

* **Mọi vector trong cùng một bảng phải do cùng một mô hình tạo ra.** Cosine giữa hai mô hình khác nhau là vô nghĩa. Trước đây danh sách dự phòng `text-embedding-004 → embedding-001 → gemini-embedding-001` có thể âm thầm trộn các không gian vector. Nay có thể ghim bằng biến môi trường `GEMINI_EMBEDDING_MODEL`; khi phải dùng model dự phòng, hệ thống ghi cảnh báo. **Đổi model thì phải nạp lại (backfill) toàn bộ embedding.**
* **Chuẩn hóa L2** mọi vector về độ dài 1 (`GeminiApiClient.l2Normalize`). Với `gemini-embedding-001` cắt còn 768 chiều, vector không còn độ dài 1; chuẩn hóa làm cosine và tích vô hướng nhất quán.

### 2.2. Thời điểm sinh vector của bài viết

`PostPublicationHelper.notifyPostPublished` là điểm chung của **mọi đường xuất bản** (đăng thẳng, xử lý xong ảnh/video, hẹn giờ, nhóm duyệt bài) và gọi `vectorizePostAsync`. Trước đây chỉ đường "đăng thẳng" được vector hóa, nên bài có media, bài hẹn giờ và bài nhóm không có embedding và không xuất hiện trong tìm kiếm ngữ nghĩa/đề xuất.

Việc sinh vector chạy nền trên **hàng đợi luồng riêng có chặn trên** (`EmbeddingTaskExecutor`: 3 luồng, hàng đợi 1000), thay cho `CompletableFuture.runAsync` trên `ForkJoinPool` dùng chung. Hết chỗ thì bỏ tác vụ và để `backfillEmbeddings` nạp bù.

### 2.3. Lược đồ và chỉ mục

```sql
CREATE EXTENSION IF NOT EXISTS vector;
-- post_embeddings, user_embeddings, group_embeddings: (id PK, embedding vector(768), updated_at)
-- user_search_interests: 1 dòng cho mỗi người dùng (user_id PK, query_text, embedding, updated_at)
CREATE INDEX idx_post_embeddings_hnsw ON post_embeddings
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

Khi truy vấn đặt `SET LOCAL hnsw.ef_search = 200`, vì mặc định 40 sẽ cắt kết quả còn 40 dòng dù `LIMIT` lớn hơn.

### 2.4. Truy vấn "bài liên quan / người dùng tương tự" phải dùng được HNSW

Dạng self-join `p1 JOIN p2 ... ORDER BY p2.embedding <=> p1.embedding` **không dùng được chỉ mục**: planner phải tính khoảng cách với mọi dòng rồi sắp xếp, tức O(N log N). Mẫu chuẩn của pgvector là `LATERAL` + `LIMIT`, trong đó vector gốc là hằng đối với chỉ mục:

```sql
SELECT p2.post_id, 1 - (p2.embedding <=> src.embedding) AS similarity
FROM (SELECT embedding FROM post_embeddings WHERE post_id = ?) src,
     LATERAL (SELECT pe.post_id, pe.embedding FROM post_embeddings pe
              WHERE pe.post_id <> ? ORDER BY pe.embedding <=> src.embedding LIMIT ?) p2
WHERE 1 - (p2.embedding <=> src.embedding) >= ?
ORDER BY p2.embedding <=> src.embedding;
```

Áp dụng cho `findRelatedPosts` (post-service) và `findSimilarUsers` (user-service).

### 2.5. Cơ sở toán học

Cosine distance của pgvector: `u <=> v = 1 − (u·v)/(‖u‖‖v‖)`; độ tương đồng = `1 − (u <=> v)`.

> **Không dùng ngưỡng cosine cố định.** Embedding thường dồn cụm: ở dữ liệu thật, cosine giữa hai bài *không liên quan* đã vào khoảng 0,55–0,63 và mức nền này đổi theo từng truy vấn. Vì vậy tìm kiếm dùng **z-score theo phân phối nền của chính truy vấn** (mục 3.3).

---

## 3. Tìm kiếm lai nhiều tầng

`PostSearchService.search` chạy theo kiến trúc cascade; mỗi tầng tự suy giảm khi thiếu dữ liệu (thiếu embedding → còn từ khóa; thiếu chỉ mục full-text → còn vector).

```
Truy vấn ─► Hiểu truy vấn (SearchTextAnalyzer: bỏ dấu, từ khóa, hashtag, khớp tiền tố từ cuối)
        ├─► A. Chỉ mục đảo GIN (tsvector)         ≤ 200 bài
        ├─► B. HNSW cosine, lọc z ≥ 2             ≤ 150 bài
        └─► C. Đồ thị xã hội (bài của mình/bạn bè/đang theo dõi) ≤ 100 bài
              └─► RRF  ► giữ 120 ứng viên ► lọc quyền xem ► L2: BM25 + cosine + cụm + hashtag
                                         ► điều chỉnh bởi xã hội/tương tác/độ mới/uy tín ► đa dạng hóa ► ≤ 200 kết quả
```

### 3.1. Reciprocal Rank Fusion (`RankFusion`)

Trộn các danh sách có thang điểm không so sánh được (BM25 và cosine) mà không cần chuẩn hóa điểm:

$$\mathrm{RRF}(d)=\sum_{L\ni d}\frac{1}{k+\mathrm{rank}_L(d)},\qquad k=60$$

### 3.2. BM25 (`Bm25`)

$$\mathrm{idf}(t)=\ln\!\Big(1+\frac{N-df+0.5}{df+0.5}\Big),\qquad
\mathrm{score}(d,q)=\sum_{t}\mathrm{idf}(t)\frac{tf\,(k_1+1)}{tf+k_1\,(1-b+b\,|d|/\mathrm{avgdl})}$$

với `k1 = 1.2`, `b = 0.75`. Từ khóa gõ có dấu khớp đúng dấu; gõ không dấu khớp theo dạng đã bỏ dấu. Điểm được chuẩn hóa tuyệt đối `min(1, BM25 / Σ idf)` để một khớp yếu trên từ phổ biến không bị coi là khớp 100%.

### 3.3. Ngữ nghĩa theo z-score của truy vấn

`sampleQueryBaseline` lấy mẫu ~400 dòng (`TABLESAMPLE BERNOULLI`), tính trung vị và σ bền vững `σ = (p90 − p10) / 2.563`. Khi đó `z = (cosine − median)/σ`; `ngữ_nghĩa = clamp((z − 2)/(6 − 2), 0, 1)`. Bài chỉ gần nghĩa (không khớp từ khóa) phải đạt `z ≥ 2.8` mới vào kết quả.

### 3.4. Điểm L2

```
relevance = 0.40·BM25chuẩn + 0.35·ngữ_nghĩa + 0.15·cụm_khớp + 0.10·hashtag
prior     = 0.50·xã_hội + 0.30·tương_tác + 0.20·độ_mới          (độ mới: nửa đời 30 ngày)
score     = relevance · (0.6 + 0.4·prior) · trọng_số_uy_tín_tác_giả
```

`relevance` là cổng chính: bài không liên quan không thể lên đầu nhờ bạn bè hay nhiều lượt thích. Sau đó cắt đuôi theo ngưỡng tương đối `0.35 ×` điểm cao nhất rồi đa dạng hóa theo tác giả. Các trọng số đặt tay (chưa có nhật ký nhấp chuột để huấn luyện).

---

## 4. Xếp hạng bảng tin

`FeedRankingService.compute` lấy tối đa 400 bài ứng viên (cửa sổ 14 → 60 → 365 ngày, nới dần nếu < 40 bài), lọc quyền xem, chấm điểm, đa dạng hóa, giữ ≤ 200 bài trong một **bản chụp** lưu ở Redis để phân trang ổn định.

### 4.1. Chế độ RELEVANCE ("Tất cả")

$$\mathrm{Score}=(\mathrm{Affinity}+0.6\cdot\mathrm{Engagement}+0.4)\cdot(0.2+0.8\cdot\mathrm{Freshness})\cdot\mathrm{Quality}$$

* **Engagement** `= ln(1 + thích + 2·bình luận + 3·chia sẻ)` (lợi ích cận biên giảm dần).
* **Freshness** `= 0.5^(tuổi_giờ / 24)` — **nửa đời 24 giờ** (không phải 36 giờ như tài liệu cũ).
* **Quality** `= 1 + 0.15 (có media) + 0.10 (nội dung > 200 ký tự)`; bài ghim × 1.1.
* **Affinity** (cộng dồn):

| Thành phần | Giá trị |
|:---|:---|
| Bạn bè / đang theo dõi / bài của mình | 3.0 / 2.0 / 0.5 (+1.5 nếu đăng trong 2 giờ) |
| Bài trong nhóm mình tham gia | +1.5 |
| Lịch sử tương tác 30 ngày với tác giả | `min(3.0, 1.2·ln(1 + n))` |
| Chủ đề vừa tìm (≥ 0.40 cosine, 24 giờ) | `3.5 · similarity` |
| Lọc cộng tác (mục 5.2) | `2.5 · CF`, CF ∈ [0, 1] |
| Hồ sơ sở thích (mục 5.1) | `2.0 · ContentBoost`, ∈ [0, 1] |

Điểm nhân thêm **trọng số uy tín** của tác giả (tác giả điểm thấp bị giảm ưu tiên).

### 4.2. Đa dạng hóa tác giả (`PostRelevanceScorer.diversify`)

Chọn tham lam: mỗi lần lấy mục có điểm hiệu chỉnh cao nhất, nhân `0.75^k` với k là số bài của cùng tác giả đã chọn và nhân thêm `0.5` nếu sẽ thành 3 bài liên tiếp của một người. Chi phí O(limit × pool) = 200 × 400, không đáng kể.

### 4.3. Chế độ TRENDING ("Nổi bật")

Xem mục 6.1.

### 4.4. Tối ưu: dùng chung dữ liệu tương tác giữa các lần xếp hạng

Tập tương tác (thích, bình luận, chia sẻ 60 ngày, ≤ 20 000 dòng mỗi loại) **giống nhau với mọi người xem**, nhưng trước đây được truy vấn lại ba bảng cho *mỗi* lần tính bảng tin. Nay `FeedRankingService.loadInteractionData` giữ trong bộ nhớ **120 giây** và dùng chung (kiểm tra hai lần để chỉ một luồng nạp; kết quả lỗi không được lưu đệm). Độ trễ vài phút là không đáng kể vì tín hiệu cộng tác dùng nửa đời 14 ngày.

---

## 5. Đề xuất: chủ đề vừa tìm, hồ sơ sở thích, lọc cộng tác

Mô hình lai kiểu chuyển đổi (switching hybrid): người xem ít hơn 2 tương tác (khởi đầu lạnh) không có điểm CF; ít hơn 3 bài có vector thì không có điểm hồ sơ; khi đó bảng tin dựa vào chủ đề vừa tìm và quan hệ xã hội.

### 5.0. Chủ đề vừa tìm (Search-biased Retargeting)

Mỗi lần tìm kiếm ngữ nghĩa, vector truy vấn được lưu vào `user_search_interests` (một dòng/người) và bản chụp bảng tin bị hủy. Trong 24 giờ sau, bài ứng viên có cosine với vector đó ≥ 0.40 được cộng `3.5 × similarity` và gắn lý do *"Gợi ý theo chủ đề bạn vừa tìm: …"* (Explainable AI).

### 5.1. Hồ sơ sở thích (Content-based, centroid kiểu Rocchio) — `UserInterestProfile`

```
w(i)      = trọng_số_loại · 0.5^(tuổi_ngày / 30)           (thích 1, bình luận 2, chia sẻ 3)
profile   = chuẩn_hóa( Σ w(i)·v(i)/‖v(i)‖ / Σ w(i) )       (giữ 50 bài nặng nhất; cần ≥ 3 bài có vector)
ContentBoost(a) = clamp( (cos(profile, v(a)) − 0.45) / 0.55 , 0, 1 )
```

Cosine được tính **trong cơ sở dữ liệu** (`computeSimilaritiesForPosts`), nên không phải tải vector của ứng viên về ứng dụng. Bài đã tương tác không được cộng lại.

### 5.2. Lọc cộng tác theo bài viết (Item-based CF) — `ItemCollaborativeFilter`

Ma trận thưa người dùng × bài; `w(u,i) = Σ trọng_số_loại · 0.5^(tuổi_ngày/14)`. Độ giống giữa hai bài là cosine hai cột (tính trên người dùng *khác* người xem) kèm hệ số co:

$$\mathrm{sim}(a,b)=\cos(a,b)\cdot\frac{n}{n+\lambda},\quad \lambda=2$$

$$\mathrm{CF}(v,a)=\min\!\Big(1,\ \sum_b \mathrm{sim}(a,b)\cdot\frac{w(v,b)}{\max_b w(v,b)}\Big)$$

Chi phí tỉ lệ với số cặp (ứng viên, bài người xem đã tương tác) thực sự đồng xuất hiện, không phải kích thước cả ma trận.

---

## 6. Thịnh hành và hashtag đang nổi

### 6.1. Suy giảm trọng lực kiểu Hacker News

$$\mathrm{Trending}=\frac{\text{thích}+2\cdot\text{bình luận}+3\cdot\text{chia sẻ}}{(\mathrm{tuổi\_giờ}+2)^{1.3}}$$

### 6.2. Hashtag bùng nổ (`HashtagTrendAnalyzer`)

Đếm số bài chứa hashtag trong cửa sổ hiện tại `c` và cửa sổ liền trước cùng độ dài `p`:

```
tăng trưởng (%) = (c − p) / (p + 1) · 100                 (làm mượt Laplace)
điểm bùng nổ    = (c − (p + 1)) / √(p + 1)                (z-score xấp xỉ Poisson)
```

Giữ hashtag `c ≥ 1` và `c ≥ p`; gắn VIRAL khi điểm ≥ 3 và `c ≥ 3`. Cửa sổ thích ứng 24 → 72 → 168 giờ cho đến khi có ≥ 5 lượt hashtag.

---

## 7. Gợi ý kết bạn

`FriendSuggestionEngine` gom ứng viên từ: bạn của bạn (bậc 2), thành viên nhóm chung (nhóm nhỏ ưu tiên hơn nhóm quá đông), người theo dõi qua lại, và nhóm khám phá khi còn ít ứng viên. Loại: chính mình, bạn hiện tại, người bị chặn (hai chiều), lời mời đang chờ/đã bị từ chối.

**Bạn chung tính theo Adamic–Adar:** mỗi bạn chung đóng góp `1 / ln(số_bạn_của_họ + 2)` — một người quen biết tất cả mọi người ít giá trị hơn một người có vòng quan hệ nhỏ. Tín hiệu xã hội thật luôn xếp trên tín hiệu chỉ dựa vào hồ sơ (điểm cơ sở ≥ 5 so với tối đa ≈ 4); có nhiễu tất định tối đa 0.3 để danh sách không đứng yên; kết quả lưu đệm 3 phút.

---

## 8. Kiểm duyệt nội dung ba tầng (Aho–Corasick)

1. **Tầng 1 – từ khóa:** `FastSensitiveWordFilter` + `AhoCorasickMatcher`.
2. **Tầng 2 – ngữ cảnh:** Gemini trả điểm độc hại 0–1, danh mục, lý do (JSON có cấu trúc). Gemini lỗi thì chỉ còn tầng 1.
3. **Tầng 3 – con người:** kiểm duyệt viên thẩm định; từ khóa mới do Gemini trích xuất vào bảng chờ duyệt (độ tin cậy 0.85), duyệt xong thì nạp lại từ điển.

Ngưỡng đọc từ cấu hình: **0.40** (tạm ẩn + gắn cờ), **0.65** (ẩn), **0.85** (xóa; chỉ khi có báo cáo của người dùng). Điểm tầng 1: CRITICAL ≥ 0.85, HIGH 0.65–0.84, MEDIUM 0.40–0.64; mỗi từ khớp cùng mức cộng 0.05 trong trần của bậc.

### 8.1. Aho–Corasick thật sự (trước đây tài liệu nói AC nhưng mã dùng regex)

Cách cũ biên dịch **một regex cho mỗi từ khóa ở mỗi lần quét**: O(K·(N + chi phí biên dịch)). Nay `AhoCorasickMatcher` dựng một trie có *liên kết thất bại* (BFS) và *liên kết từ điển*; mỗi văn bản chỉ một lượt quét **O(N + số lần khớp)**, không phụ thuộc số từ khóa K. Một lần khớp chỉ tính khi đúng **ranh giới từ** (ký tự trước/sau không phải chữ hoặc số Unicode), nên "điểm" không dính "đĩ", "buổi" không dính "buồi".

Hai automaton: dạng **có dấu** (bằng chứng mạnh) và dạng **không dấu** (bằng chứng yếu, hạ một bậc: CRITICAL→HIGH→MEDIUM, MEDIUM bị bỏ), chỉ dùng cho từ khóa không dấu ≥ 4 ký tự.

### 8.2. An toàn luồng

Trước đây `reloadCache()` xóa rồi nạp lại ba bản đồ đang được quét, nên có khoảng **từ điển rỗng** làm lọt nội dung. Nay từ điển được dựng ở ngoài rồi **hoán đổi nguyên khối**; automaton là bản chụp bất biến, được dựng lại lười (cờ `dirty`) một lần cho cả loạt thêm/xóa lẻ.

---

## 9. Giới hạn tốc độ Token Bucket tại API Gateway

Mỗi bucket có dung lượng và tốc độ nạp liên tục; mỗi yêu cầu tiêu 1 token, hết token trả HTTP 429 + `Retry-After`, `X-RateLimit-*`.

| Tầng | Dung lượng | Nạp |
|:---|:---:|:---|
| Xác thực (login/register/forgot/mfa) | 5 | 1 token / 30 giây |
| Trí tuệ nhân tạo (`/api/v1/ai`) | 5 | 1 token / 10 giây |
| Ghi dữ liệu (POST/PUT/PATCH/DELETE posts, media, groups, comments) | 10 | 1 token / 30 giây |
| Đọc dữ liệu | 300 | ≈ 30 token / giây |

### 9.1. Các lỗi đã sửa

* **Dọn bucket không bao giờ chạy** (rò rỉ bộ nhớ): điều kiện rảnh so với `lastRefillTimestamp` nhưng `refill()` vừa ghi đè nó bằng "bây giờ". Nay có `lastAccessTimestamp` riêng.
* **Né giới hạn đăng nhập bằng token giả:** khóa bucket lấy từ `token.hashCode()` của header chưa xác thực, nên gửi mỗi yêu cầu một `Bearer` ngẫu nhiên luôn rơi vào bucket mới. Nay tầng xác thực **chỉ tính theo IP**; các tầng khác dùng SHA-256 64 bit của token **và** một bucket theo IP có hạn mức gấp 5 lần, kiểm tra *trước* khi tạo bucket theo token.
* **Giả mạo `X-Forwarded-For`:** phần tử đầu do client tự gửi. Nay lấy phần tử thứ `trusted-proxy-hops` tính từ cuối (`rate-limit.trusted-proxy-hops`, mặc định 1; đặt 0 để bỏ qua tiêu đề).
* **Đường dẫn embedding công khai:** ai-service mở `/api/v1/ai/embeddings/**` cho các dịch vụ nội bộ, nhưng gateway lại route ra Internet nên ai cũng đốt được hạn ngạch Gemini. Gateway nay chặn đường dẫn này (post-service/user-service gọi thẳng, không qua gateway).

> **Hạn chế còn lại:** bộ đếm nằm trong bộ nhớ từng bản gateway; chạy nhiều bản thì hạn mức nhân lên. Xem mục 11.

---

## 10. Nhật ký tối ưu và sửa lỗi đợt này

| # | Vị trí | Vấn đề | Cách xử lý | Loại |
|:-:|:---|:---|:---|:---|
| 1 | `RateLimitingGlobalFilter` | Dọn bucket không chạy → rò rỉ bộ nhớ | `lastAccessTimestamp` riêng | Lỗi |
| 2 | `RateLimitingGlobalFilter` | Né giới hạn bằng Bearer/XFF giả | IP cho tầng auth, SHA-256 + IP guard, trusted-proxy-hops | Bảo mật |
| 3 | `RouteLocatorConfig` | `/api/v1/ai/embeddings` truy cập công khai | Chặn ở gateway | Bảo mật |
| 4 | `FastSensitiveWordFilter` | Mô tả AC nhưng mã dùng regex/từ khóa, có khoảng hở khi reload | `AhoCorasickMatcher` + bản chụp bất biến | Thuật toán + lỗi |
| 5 | `PostPublicationHelper` | Bài media/hẹn giờ/nhóm không có embedding | Vector hóa ở điểm xuất bản chung | Lỗi chức năng |
| 6 | `PostEmbeddingService` (và user/group) | `runAsync` trên ForkJoinPool dùng chung | Hàng đợi luồng riêng có chặn trên | Hiệu năng |
| 7 | `findRelatedPosts`, `findSimilarUsers` | Self-join không dùng được HNSW (O(N log N)) | `LATERAL` + `LIMIT` | Thuật toán |
| 8 | `FeedRankingService` | Nạp lại ≤ 60 000 dòng tương tác cho mỗi lần xếp hạng | Cache 120 giây dùng chung | Hiệu năng |
| 9 | `Post` entity | Mọi `save()` ghi lại toàn bộ cột từ bản đọc cũ → có thể "hồi sinh" bài vừa bị xóa/ẩn, mất bộ đếm | `@DynamicUpdate` | Lỗi đồng thời |
| 10 | `SharePostCommandHandler` | `shareCount + 1` rồi `save` làm mất lượt chia sẻ đồng thời | `UPDATE ... SET share_count = share_count + 1` | Lỗi đồng thời |
| 11 | `GeminiApiClient` | Fallback model trộn không gian vector; chưa chuẩn hóa | Ghim model bằng cấu hình, chuẩn hóa L2, cảnh báo | Lỗi dữ liệu |
| 12 | FE `axiosClient.ts` | Lấy dấu vân tay ném lỗi ngoài `try` → `isRefreshing` kẹt, mọi 401 sau treo | Đưa vào `try` | Lỗi |
| 13 | FE `SidebarLeftShortcuts.tsx` | Gọi hook sau `return` có điều kiện (vi phạm Rules of Hooks) | Dời `return` xuống sau các hook | Lỗi |

---

## 11. Các giải thuật có thể áp dụng thêm

Sắp theo mức lợi ích / công sức đối với hệ thống hiện tại.

### 11.1. Đồ thị xã hội (phần "graph")

| Giải thuật | Áp dụng vào | Lợi ích | Công sức |
|:---|:---|:---|:---:|
| **Personalized PageRank / Random Walk with Restart** | Gợi ý kết bạn: xếp hạng người dùng bậc 2–3 theo xác suất đến từ người xem, thay cho chỉ bậc 2 | Bắt được quan hệ gián tiếp; tự cân bằng bậc nút | Trung bình |
| **Phát hiện cộng đồng (Louvain / Label Propagation)** | Gợi ý nhóm, gom người dùng cùng cộng đồng để điều chỉnh bảng tin | Gợi ý nhóm dựa trên cấu trúc thật thay vì chỉ embedding mô tả | Trung bình |
| **Jaccard / SimRank** | Độ giống giữa hai người dùng theo tập bạn/nhóm | Bổ sung cho Adamic–Adar | Thấp |

### 11.2. Xếp hạng và đề xuất

| Giải thuật | Áp dụng vào | Lợi ích | Công sức |
|:---|:---|:---|:---:|
| **MMR (Maximal Marginal Relevance) trên embedding** | `diversify`: đa dạng theo *chủ đề*, không chỉ theo tác giả | Tránh 5 bài cùng chủ đề liên tiếp | Thấp |
| **Contextual bandit / Thompson Sampling** | Khám phá bài mới/tác giả mới có ít tương tác | Giải bài toán "cold start" của bài viết | Trung bình |
| **Phân rã ma trận ngầm (ALS) tính định kỳ** | Thay CF tại chỗ khi dữ liệu lớn (đã nêu ở phần hướng phát triển) | Giảm chi phí theo yêu cầu; dùng được ma trận lớn | Cao |
| **Learning-to-Rank (LambdaMART) từ nhật ký nhấp chuột** | Thay trọng số đặt tay của `SearchScorer`/`PostRelevanceScorer` | Trọng số học được, đo được bằng NDCG | Cao (cần log) |
| **Wilson score lower bound** | Xếp hạng theo tỉ lệ thích khi ít lượt | Bài ít lượt không bị đánh giá quá cao | Thấp |
| **Cross-encoder / rerank bằng LLM** | Xếp lại top-20 kết quả tìm kiếm | Chất lượng cao hơn, tốn quota | Trung bình |

### 11.3. Hạ tầng và quy mô

| Giải thuật | Áp dụng vào | Lợi ích | Công sức |
|:---|:---|:---|:---:|
| **Token Bucket phân tán bằng Redis + Lua (hoặc GCRA)** | Gateway khi chạy nhiều bản | Hạn mức đúng khi scale ngang | Trung bình |
| **W-TinyLFU (Caffeine)** | Cache vector truy vấn (đang là LRU thủ công) | Tỉ lệ trúng cache cao hơn, an toàn luồng | Thấp |
| **Bloom filter / bitmap trong Redis** | `PostSeenService` (bài đã xem) | Kiểm tra "đã xem" O(1), tiết kiệm bộ nhớ | Thấp |
| **Count-Min Sketch + HyperLogLog** | Đếm hashtag/lượt xem ở quy mô lớn | Bộ nhớ hằng số | Trung bình |
| **EWMA thay cho hai cửa sổ cố định** | `HashtagTrendAnalyzer` | Mượt hơn, không "nhảy" ở ranh giới cửa sổ | Thấp |
| **pgvector `halfvec` / lượng tử hóa nhị phân + iterative scan** | `post_embeddings` | Giảm ~2× bộ nhớ chỉ mục; lọc kèm điều kiện không bị thiếu kết quả | Thấp–TB |

### 11.4. Kiểm duyệt

| Giải thuật | Áp dụng vào | Lợi ích |
|:---|:---|:---|
| **SimHash / MinHash** | Phát hiện spam/bài trùng lặp gần giống | Chặn lặp nội dung rẻ, không tốn Gemini |
| **Perceptual hash (pHash)** | Ảnh vi phạm đã bị gỡ đăng lại | Chặn ảnh lặp trước khi gọi mô hình |

---

## 12. Về số liệu hiệu năng

Bản tài liệu cũ có bảng đo trên máy phát triển (HNSW so với quét tuần tự ở 1 000/10 000/100 000 bài; độ trễ đăng bài 18–25 ms; tìm kiếm 180–320 ms…). **Các con số đó chưa được đo lại sau đợt tối ưu này và không có kịch bản đo kèm trong kho mã**, nên tài liệu này không lặp lại chúng như số liệu đã kiểm chứng. Những gì có thể khẳng định từ mã nguồn là **độ phức tạp** ở bảng mục 1 và các thay đổi ở mục 10.

Muốn có số liệu để đưa vào báo cáo: chạy `EXPLAIN (ANALYZE, BUFFERS)` cho hai truy vấn mục 2.4 trước/sau, và đo thời gian `FeedRankingService.compute` với dữ liệu thật (log mức DEBUG đã có sẵn các mốc thời gian cho tìm kiếm).
