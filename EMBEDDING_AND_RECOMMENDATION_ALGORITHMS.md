# TÀI LIỆU KỸ THUẬT: VECTOR EMBEDDING VÀ CÁC GIẢI THUẬT TÌM KIẾM, ĐỀ XUẤT, KIỂM DUYỆT (KLTN SOCIAL)

> **Phiên bản:** đồng bộ với mã nguồn và báo cáo `KhoaLuan_MangXaHoi_v22.docx`.
> **Nguyên tắc của tài liệu:** mọi công thức và hằng số dưới đây được đối chiếu trực tiếp với mã nguồn (đường dẫn ghi ở từng mục).
> Chỗ nào là số đo thực nghiệm chưa đo lại sau đợt tối ưu gần nhất thì ghi rõ.

**Công nghệ lõi:** Java 21, Spring Boot 3.x, Google Gemini (embedding 768 chiều + sinh văn bản), PostgreSQL + `pgvector` (HNSW), Redis, Apache Kafka.

---

## MỤC LỤC

1. [Bảng tổng hợp các giải thuật](#1-bảng-tổng-hợp-các-giải-thuật)
2. [Vector embedding và chỉ mục HNSW](#2-vector-embedding-và-chỉ-mục-hnsw)
3. [Tìm kiếm ngữ nghĩa nhiều tầng (không dùng full-text)](#3-tìm-kiếm-ngữ-nghĩa-nhiều-tầng-không-dùng-full-text)
4. [Xếp hạng bảng tin và đa dạng hoá MMR](#4-xếp-hạng-bảng-tin-và-đa-dạng-hoá-mmr)
5. [Đề xuất: chủ đề vừa tìm, hồ sơ sở thích, lọc cộng tác](#5-đề-xuất-chủ-đề-vừa-tìm-hồ-sơ-sở-thích-lọc-cộng-tác)
6. [Xu hướng cộng đồng: bài nổi, hashtag, chủ đề](#6-xu-hướng-cộng-đồng-bài-nổi-hashtag-chủ-đề)
7. [Đồ thị xã hội: gợi ý kết bạn và gợi ý nhóm](#7-đồ-thị-xã-hội-gợi-ý-kết-bạn-và-gợi-ý-nhóm)
8. [Kiểm duyệt nội dung ba tầng (Aho–Corasick)](#8-kiểm-duyệt-nội-dung-ba-tầng-aho–corasick)
9. [Giới hạn tốc độ Token Bucket tại API Gateway](#9-giới-hạn-tốc-độ-token-bucket-tại-api-gateway)
10. [Đánh giá offline và học trọng số](#10-đánh-giá-offline-và-học-trọng-số)
11. [Nhật ký tối ưu và sửa lỗi](#11-nhật-ký-tối-ưu-và-sửa-lỗi)
12. [Các giải thuật có thể áp dụng thêm](#12-các-giải-thuật-có-thể-áp-dụng-thêm)
13. [Về số liệu hiệu năng](#13-về-số-liệu-hiệu-năng)

---

## 1. Bảng tổng hợp các giải thuật

| Bài toán | Giải thuật | Độ phức tạp chính | Mã nguồn |
|:---|:---|:---|:---|
| Tìm vector gần nhất | HNSW (ANN) trên pgvector, cosine | ≈ O(log N) | `PostEmbeddingService.searchSimilar` |
| Tìm kiếm bài viết | Ngữ nghĩa: ANN toàn cục + đồ thị xã hội + hashtag, hợp nhất RRF, xếp hạng lại bằng trọng số **học được** | O(K log K), K ≤ 370 ứng viên | `search/PostSearchService`, `RankFusion`, `SearchScorer` |
| Đa dạng hoá kết quả | **MMR** trên embedding, sàn cosine thích nghi, kèm phạt theo tác giả | O(limit · n · d), n ≤ 120 | `ranking/MmrReranker` |
| Đánh giá chất lượng | Precision@k, Recall@k, MAP, MRR, NDCG@k; ablation 5 phương án; kiểm định chéo | O(Q · K log K) | `search/eval/*` |
| Học trọng số xếp hạng | Tìm kiếm theo toạ độ (coordinate ascent) tối đa hoá NDCG@k, có điều chuẩn L2 | O(sweeps · 14 · Q · K log K) | `search/eval/SearchWeightTuner` |
| Xếp hạng bảng tin | Điểm đa nhân tố: Affinity × Engagement(log) × Freshness(bán rã mũ), đa dạng hoá MMR | O(V + R) | `ranking/PostRelevanceScorer`, `FeedRankingService` |
| Đề xuất theo hành vi | Lọc cộng tác theo bài viết (Item-based CF), cosine có co (shrinkage) | O(số cặp đồng xuất hiện) | `ranking/ItemCollaborativeFilter` |
| Đề xuất theo nội dung | Hồ sơ sở thích (centroid kiểu Rocchio) | O(M·D), M ≤ 50, D = 768 | `ranking/UserInterestProfile` |
| Bài thịnh hành | Suy giảm trọng lực kiểu Hacker News | O(1) mỗi bài | `PostRelevanceScorer.trending` |
| Hashtag nổi | So hai cửa sổ liền kề, z-score xấp xỉ Poisson | O(số bài) | `admin-service/HashtagTrendAnalyzer` |
| Chủ đề nổi | **k-means cầu** (k-means++) + chọn k bằng **silhouette**, đo tăng trưởng từng cụm | O(n²d) một lần + O(k·n·d·iter) | `topic/TopicClusterer`, `TrendingTopicService` |
| Gợi ý kết bạn | Adamic–Adar + **Personalized PageRank** (bậc 3) + cộng đồng + tín hiệu nhóm/theo dõi/hồ sơ | O(iter · \|E\|) trên đồ thị ego | `user-service/.../graph/*`, `suggestion/*` |
| Phát hiện cộng đồng | **Label Propagation** (tất định theo hạt giống) | ≈ O(iter · \|E\|) | `graph/LabelPropagation` |
| Gợi ý nhóm | Lọc cộng tác trên đồ thị: Σ PPR của thành viên, giảm theo kích thước nhóm (kiểu IDF) | O(300 người + nhóm của họ) | `GroupRecommendationService` |
| Lọc từ khóa độc hại | Aho–Corasick, khớp theo ranh giới từ | O(N + số lần khớp) | `ai-service/.../engine/AhoCorasickMatcher` |
| Chống lạm dụng API | Token Bucket phân tầng + bucket chặn theo IP | O(1) mỗi yêu cầu | `api-gateway/.../RateLimitingGlobalFilter` |
| Trợ lý hỏi đáp | RAG dùng chính đường ống tìm kiếm ngữ nghĩa, tôn trọng quyền xem | — | `ai-service/.../RagAssistantService` |

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

## 3. Tìm kiếm ngữ nghĩa nhiều tầng (không dùng full-text)

> **Quyết định thiết kế:** hệ thống **không dùng tìm kiếm toàn văn** (`tsvector`/GIN, `unaccent`, BM25). Sức mạnh của tìm kiếm nằm ở không gian vector, đồ thị quan hệ và xếp hạng học được. Khi khởi động, post-service gỡ cột `search_tsv`, trigger, hàm và chỉ mục GIN do phiên bản cũ tạo ra để không tốn chi phí ghi.

`PostSearchService.search` chạy theo kiến trúc cascade; thiếu embedding của truy vấn (ai-service lỗi) thì chỉ còn nhánh hashtag.

```
Truy vấn ─► vector truy vấn (Gemini, cache LRU 30 phút) + phân phối nền cosine của CHÍNH truy vấn (mẫu ~400 bài)
        ├─► A. HNSW cosine toàn cục, lọc z ≥ 2                                   ≤ 150 bài
        ├─► B. Đồ thị xã hội: cosine CHÍNH XÁC trên ≤ 300 bài 90 ngày gần nhất
        │       của bạn bè/người theo dõi/chính mình, lọc z ≥ 2                    ≤ 100 bài
        └─► C. Hashtag trong truy vấn (khớp chính xác qua bảng hashtag)           ≤ 50 bài / hashtag
              └─► RRF ► giữ 120 ứng viên ► lọc trạng thái + quyền xem
                    ► L2: ngữ nghĩa + cụm khớp + hashtag, điều chỉnh bởi xã hội/tương tác/độ mới/uy tín
                    ► cắt đuôi tương đối 0.35 ► MMR đa dạng hoá ► ≤ 200 kết quả
```

### 3.1. Vì sao nhánh xã hội dùng cosine chính xác

Tập bài của vòng quan hệ nhỏ (≤ 300) nên tính cosine chính xác trong CSDL (`computeSimilaritiesForPosts`) thay vì ANN. Nhờ vậy bài của người quen không bị HNSW toàn cục bỏ sót khi có rất nhiều bài công khai gần nghĩa hơn một chút — đây là phần mà tìm kiếm thuần vector thường làm kém.

### 3.2. Reciprocal Rank Fusion (`RankFusion`)

Trộn ba danh sách có thang điểm không so sánh được mà không cần chuẩn hóa điểm:

$$\mathrm{RRF}(d)=\sum_{L\ni d}\frac{1}{k+\mathrm{rank}_L(d)},\qquad k=60$$

Bài được cả nhánh toàn cục lẫn nhánh xã hội (hoặc hashtag) tìm thấy tự nhiên có điểm cao hơn.

### 3.3. Ngữ nghĩa theo z-score của truy vấn

Cosine thô không dùng được: embedding dồn cụm nên hai bài *không liên quan* đã có cosine 0,55–0,63 và mức nền này đổi theo truy vấn. `sampleQueryBaseline` lấy mẫu ~400 dòng (`TABLESAMPLE BERNOULLI`), tính trung vị và σ bền vững `σ = (p90 − p10) / 2.563`; khi đó `z = (cosine − median)/σ`, `ngữ_nghĩa = clamp((z − 2)/(6 − 2), 0, 1)`. Bài chỉ gần nghĩa (không khớp cụm/hashtag) phải đạt `z ≥ 2.8` mới vào kết quả.

### 3.4. Điểm L2 với bộ trọng số học được

```
relevance = (w_ns·ngữ_nghĩa + w_cụm·cụm_khớp + w_ht·hashtag) / Σw
prior     = (w_xh·xã_hội + w_tt·tương_tác + w_mới·độ_mới) / Σw          (độ mới: nửa đời 30 ngày)
score     = relevance · ((1 − span) + span·prior) · trọng_số_uy_tín_tác_giả
```

Mặc định `(w_ns, w_cụm, w_ht) = (0.70, 0.20, 0.10)`, `(w_xh, w_tt, w_mới) = (0.50, 0.30, 0.20)`, `span = 0.40`. **Đây là 7 tham số được học** bằng bộ đánh giá offline (mục 10) và áp dụng qua cấu hình `SEARCH_WEIGHTS` mà không cần biên dịch lại. `relevance` là cổng chính: bài không liên quan không thể lên đầu nhờ bạn bè hay nhiều lượt thích.

`cụm_khớp` = 1.0 nếu nội dung chứa nguyên cụm truy vấn đúng dấu, 0.6 nếu chỉ khớp sau khi bỏ dấu ("cả" ≠ "cá"), 0 nếu không — so khớp trong bộ nhớ trên tập ứng viên, không dùng chỉ mục.

---

## 4. Xếp hạng bảng tin và đa dạng hoá MMR

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

### 4.2. Đa dạng hoá theo nội dung bằng MMR (`MmrReranker`)

Cách cũ chỉ phạt theo **tác giả**, nên 5 bài cùng chủ đề của 5 người vẫn đứng liền nhau. **MMR** (Carbonell & Goldstein, SIGIR 1998) chọn lần lượt từng bài sao cho cân bằng độ liên quan và độ mới lạ so với những bài đã chọn:

$$\mathrm{MMR}(i)=\lambda\cdot\hat s_i-(1-\lambda)\cdot\mathrm{red}(i\mid S),\quad \lambda=0.7,\quad \mathrm{red}(i\mid S)=\max\Big(\max_{j\in S}\mathrm{sim}(i,j),\ \mathrm{red}_{\text{tác giả}}\Big)$$

* `ŝ` là điểm chuẩn hóa theo điểm lớn nhất của tập.
* **Sàn cosine thích nghi:** cosine thô giữa hai bài bất kỳ đã ~0,6, nên nếu dùng trực tiếp thì mọi bài đều "trùng" như nhau. Sàn μ = cosine trung bình giữa mọi cặp trong tập đang xét và `sim = clamp((cos − μ)/(1 − μ), 0, 1)`: bằng 0 với cặp "bình thường", bằng 1 với cặp trùng lặp.
* **Dư thừa theo tác giả** `min(1, 0.25 + 0.25·k)` (bài thứ 2/3/4 của một người: 0.5/0.75/1.0), thêm phạt rất mạnh nếu thành 3 bài liên tiếp của một người.
* Áp dụng cho 120 bài đầu của bảng tin (RELEVANCE) và cho kết quả tìm kiếm; phần đuôi và chế độ TRENDING vẫn phạt theo tác giả. Không có vector thì quay về cách cũ nên bảng tin không bao giờ hỏng vì bước này.

Chi phí O(limit · n · d) với n ≤ 120, d = 768: vài chục ms, và kết quả được lưu trong bản chụp Redis nên không tính lại ở mỗi lần cuộn.

### 4.3. Chế độ TRENDING ("Nổi bật")

Xem mục 6.1.

### 4.4. Tối ưu: dùng chung dữ liệu tương tác giữa các lần xếp hạng

Tập tương tác (thích, bình luận, chia sẻ 60 ngày, ≤ 20 000 dòng mỗi loại) **giống nhau với mọi người xem**, nhưng trước đây được truy vấn lại ba bảng cho *mỗi* lần tính bảng tin. Nay `FeedRankingService.loadInteractionData` giữ trong bộ nhớ **120 giây** và dùng chung (kiểm tra hai lần để chỉ một luồng nạp; kết quả lỗi không được lưu đệm).

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

## 6. Xu hướng cộng đồng: bài nổi, hashtag, chủ đề

### 6.1. Suy giảm trọng lực kiểu Hacker News

$$\mathrm{Trending}=\frac{\text{thích}+2\cdot\text{bình luận}+3\cdot\text{chia sẻ}}{(\mathrm{tuổi\_giờ}+2)^{1.3}}$$

### 6.2. Hashtag bùng nổ (`HashtagTrendAnalyzer`)

Đếm số bài chứa hashtag trong cửa sổ hiện tại `c` và cửa sổ liền trước cùng độ dài `p`:

```
tăng trưởng (%) = (c − p) / (p + 1) · 100                 (làm mượt Laplace)
điểm bùng nổ    = (c − (p + 1)) / √(p + 1)                (z-score xấp xỉ Poisson)
```

Giữ hashtag `c ≥ 1` và `c ≥ p`; gắn VIRAL khi điểm ≥ 3 và `c ≥ 3`. Cửa sổ thích ứng 24 → 72 → 168 giờ cho đến khi có ≥ 5 lượt hashtag.


### 6.3. Chủ đề đang nổi bằng gom cụm embedding (`TrendingTopicService`)

Hashtag chỉ bắt được chủ đề có người gắn thẻ. Gom cụm theo embedding bắt cả các bài **cùng chủ đề nhưng dùng từ khác nhau**.

1. Lấy ≤ 500 bài công khai (ngoài nhóm) trong cửa sổ hiện tại `[now − d, now]` và các bài của cửa sổ trước `[now − 2d, now − d]`, có vector.
2. **K-means cầu** (spherical k-means, độ đo cosine): vector chuẩn hóa độ dài 1, tâm cụm là trung bình đã chuẩn hóa. Khởi tạo **k-means++** (Arthur & Vassilvitskii, 2007): tâm kế tiếp chọn với xác suất ∝ D(x)², D = 1 − cos tới tâm gần nhất, nên các tâm đầu rải xa nhau (xấp xỉ O(log k) so với tối ưu). Cụm rỗng được gieo lại ở điểm xa tâm nhất.
3. **Chọn k bằng hệ số silhouette** (Rousseeuw, 1987): `s(i) = (b(i) − a(i)) / max(a(i), b(i))`, a = khoảng cách trung bình trong cụm, b = tới cụm gần nhất khác; thử k = 2…min(8, n/3), lấy k có silhouette trung bình lớn nhất. Ma trận cosine giữa mọi cặp tính một lần (O(n²d)) và dùng chung cho mọi k.
4. Bài của cửa sổ trước được gán vào cụm có tâm gần nhất nếu không xa tâm hơn thành viên xa nhất của cụm quá 0.05 → số bài trước đó `p`.
5. Mỗi cụm: `tăng trưởng = (c − p)/(p + 1)·100`, `điểm bùng nổ = (c − (p + 1))/√(p + 1)` (giống hashtag); BÙNG NỔ khi điểm ≥ 2 và c ≥ 3. Nhãn cụm là hashtag phổ biến nhất trong cụm cùng bài đại diện (gần tâm nhất).

API: `GET /api/v1/posts/admin/trending-topics?days=7` (ADMIN/MODERATOR), hiển thị ở trang Analytics của Admin FE.

---

## 7. Đồ thị xã hội: gợi ý kết bạn và gợi ý nhóm

### 7.1. Gợi ý kết bạn

`FriendSuggestionEngine` gom ứng viên từ: bạn của bạn (bậc 2), thành viên nhóm chung (nhóm nhỏ ưu tiên hơn nhóm quá đông), người theo dõi qua lại, **người ở bậc 3 theo Personalized PageRank**, và nhóm khám phá khi còn ít ứng viên. Loại: chính mình, bạn hiện tại, người bị chặn (hai chiều), lời mời đang chờ/đã bị từ chối.

**Bạn chung — Adamic–Adar:** mỗi bạn chung đóng góp `1 / ln(số_bạn_của_họ + 2)`.

**Quan hệ gián tiếp — Personalized PageRank / Random Walk with Restart** (`PersonalizedPageRank`): người đi bộ ngẫu nhiên xuất phát từ người xem, mỗi bước với xác suất α = 0.15 quay về, còn lại đi sang một người bạn ngẫu nhiên:

$$p=\alpha\,e_s+(1-\alpha)\,P^{\top}p,\qquad P[u][v]=\tfrac{1}{\deg(u)}\ \text{nếu }u\!-\!v$$

giải bằng lặp luỹ thừa (dừng khi tổng chênh lệch L1 < 1e-7, tối đa 50 vòng; nút "dangling" đẩy khối lượng về lại nguồn). Điểm của một nút cao khi có **nhiều đường đi ngắn** từ người xem; đường dài giảm theo cấp số nhân `(1 − α)^độ_dài`. Khác Adamic–Adar (chỉ nhìn đường dài đúng 2 bước), PPR thấy được người ở **bậc 3** dù bạn chung bằng 0 và cộng dồn mọi đường đi.

*Hiệu chỉnh nút trung tâm:* phân phối dừng tỉ lệ với bậc nên "người quen tất cả mọi người" luôn nổi lên; điểm được chia cho `deg^0.5` (cùng tinh thần với `1/ln(bậc)` của Adamic–Adar), rồi đưa về (0, 1] theo ứng viên cao nhất.

**Đồ thị ego bán kính 3** (`EgoGraphService`): bậc 1 = bạn; bậc 2 = cạnh của bạn (đã có sẵn); bậc 3 = cạnh của ≤ 100 người bậc 2 có nhiều bạn chung nhất (chặn trên 20 000 cạnh) — không thể tải toàn bộ đồ thị xã hội.

**Cộng đồng — Label Propagation** (`LabelPropagation`, Raghavan–Albert–Kumara 2007): mỗi nút bắt đầu bằng nhãn riêng; lần lượt (thứ tự xáo trộn có hạt giống) nhận nhãn xuất hiện nhiều nhất ở láng giềng, hoà thì chọn nhãn nhỏ nhất; lặp đến khi không đổi (≤ 20 vòng). Chi phí gần tuyến tính theo số cạnh, không cần biết trước số cộng đồng, tất định.

Điểm: `+ 3.0·PPR_chuẩn_hóa` (reason *"Kết nối gián tiếp qua bạn bè"* khi không có bạn chung), `+ 0.8` nếu cùng cộng đồng (*"Cùng cộng đồng bạn bè"*). Tín hiệu xã hội thật (bạn chung, cùng nhóm, theo dõi) vẫn có điểm cơ sở ≥ 5; có nhiễu tất định tối đa 0.3; kết quả lưu đệm 3 phút.

### 7.2. Gợi ý nhóm bằng lọc cộng tác trên đồ thị (`GroupRecommendationService`)

Một nhóm đáng tham gia nếu những người **gần mình trong mạng quan hệ** đang ở trong đó:

```
w(m)     = PPR(m) chuẩn hóa về (0, 1]            // độ gần của thành viên m với người xem
raw(g)   = Σ_{m ∈ g} w(m) · (1 + 0.3 nếu m cùng cộng đồng với người xem)
score(g) = raw(g) / ln(2 + |g|)                   // giống IDF: nhóm quá đông kém đặc hiệu
```

Lấy 300 người gần nhất theo PPR, tra các nhóm họ đã tham gia (một truy vấn), loại nhóm mình đã tham gia/đang chờ/được mời/bị cấm. Khác gợi ý theo embedding mô tả nhóm (chỉ biết nội dung), cách này bắt được nhóm "ai quanh mình cũng đang tham gia". API: `GET /api/v1/groups?scope=recommended`.

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

## 10. Đánh giá offline và học trọng số

Lý do tồn tại: các trọng số của xếp hạng (`0.70/0.20/0.10`, `0.5/0.3/0.2`, `span`…) ban đầu là **đặt tay**. Một đề tài "AI-powered" cần chứng minh được chúng tốt hơn phương án đơn giản và tối ưu chúng theo số liệu.

### 10.1. Thước đo (`RankingMetrics`)

Nhãn nguyên `grade ∈ {0,1,2,3}`; "liên quan" khi `grade > 0`.

| Thước đo | Công thức |
|:---|:---|
| Precision@k | (số bài liên quan trong top-k) / k |
| Recall@k | (số bài liên quan trong top-k) / (tổng số bài liên quan đã gán nhãn) |
| AP / MAP | `AP = (1/R)·Σ_{i: liên quan} Precision@i`; MAP = trung bình AP trên các truy vấn |
| RR / MRR | 1 / vị trí bài liên quan đầu tiên; MRR = trung bình |
| NDCG@k | `DCG@k / IDCG@k`, `DCG@k = Σ (2^grade − 1) / log2(i + 1)` (Järvelin & Kekäläinen, 2002) |

### 10.2. Ablation (`SearchEvaluator`)

Ứng viên và đặc trưng của mỗi truy vấn được **đóng băng** rồi chấm lại bằng 5 phương án, nên so sánh là công bằng:

1. **Chỉ RRF** — thứ tự sau khi hợp nhất các nhánh truy xuất, chưa xếp hạng lại;
2. **Chỉ ngữ nghĩa** — xếp theo z-score cosine;
3. **Ngữ nghĩa + cụm + hashtag** — relevance, chưa có tín hiệu xã hội;
4. **Đầy đủ (trọng số mặc định)**;
5. **Đã học (kiểm định chéo)**.

Báo cáo còn có **chẩn đoán truy xuất** cho từng truy vấn: tỉ lệ bài liên quan đã gán nhãn mà tầng truy xuất tìm thấy — để phân biệt lỗi do truy xuất (ứng viên thiếu) với lỗi do xếp hạng.

### 10.3. Học trọng số (`SearchWeightTuner`)

**Tìm kiếm theo toạ độ** (coordinate ascent) trên 7 tham số: lần lượt thử cộng/trừ bước `δ` vào từng tham số, giữ nếu mục tiêu tăng; `δ` giảm dần `0.30 → 0.15 → 0.08 → 0.04 → 0.02`, mỗi mức lặp tối đa 25 vòng.

$J(w)=\overline{\mathrm{NDCG@k}}(w)-\lambda\,\lVert \tilde w-\tilde w_0\rVert^2,\qquad \lambda=0.02$

* NDCG@k là hàm hằng từng khúc theo trọng số nên không dùng được gradient; toạ độ + bước giảm dần đủ cho 7 tham số và không cần thư viện tối ưu.
* Số hạng điều chuẩn L2 kéo về trọng số mặc định, chống "học thuộc" khi tập nhãn nhỏ.
* **Kiểm định chéo** (`crossValidate`, 3 phần): học trên các phần còn lại, chấm trên phần chưa thấy — con số báo cáo của phương án 5 là điểm trên dữ liệu **chưa từng dùng để học**.
* Tất định: cùng dữ liệu cho cùng kết quả (có kiểm thử).

### 10.4. Quy trình thực hiện trên dữ liệu thật

1. Chuẩn bị 20–30 truy vấn đại diện (có cả truy vấn không có kết quả phù hợp, truy vấn gõ không dấu, truy vấn hashtag) và gán nhãn 0–3 cho các bài liên quan.
2. Gọi `POST /api/v1/posts/admin/search-eval` (ADMIN/MODERATOR):
```json
{ "k": 10, "folds": 3,
  "queries": [ { "query": "món ngon thanh nhiệt mùa hè",
                 "judgments": [ { "postId": "…", "grade": 3 }, { "postId": "…", "grade": 1 } ] } ] }
```
3. Đọc bảng so sánh 5 phương án và `recommendedWeights` (chuỗi 7 số); dán vào biến môi trường `SEARCH_WEIGHTS` để áp dụng.

> Các kiểm thử đơn vị (`SearchEvaluationTest`) chứng minh **hành vi** của bộ đánh giá và bộ học trên dữ liệu tổng hợp (ví dụ trọng số học được nghiêng về cụm khớp khi tín hiệu xã hội là nhiễu); chúng **không phải kết quả thực nghiệm** về chất lượng tìm kiếm của hệ thống. Số liệu thực nghiệm chỉ có sau bước 2 ở trên.

---

## 11. Nhật ký tối ưu và sửa lỗi

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
| 14 | `PostSearchService` + `PostSearchSupport` | Tìm kiếm dựa vào full-text (tsvector/GIN/BM25) | **Bỏ hẳn**; truy xuất ANN + đồ thị xã hội + hashtag, gỡ trigger/cột/chỉ mục cũ | Thiết kế |
| 15 | `SearchScorer` | 7 trọng số đặt tay | `Weights` tham số hoá + bộ đánh giá offline + học bằng coordinate ascent (mục 10) | Thuật toán |
| 16 | `FeedRankingService`, `PostSearchService` | Đa dạng hoá chỉ theo tác giả | MMR trên embedding với sàn cosine thích nghi | Thuật toán |
| 17 | `FriendSuggestionEngine` | Chỉ thấy bạn chung bậc 2 | Personalized PageRank bậc 3 + Label Propagation | Thuật toán |
| 18 | `GroupRecommendationService` | Chưa có gợi ý nhóm theo hành vi | Lọc cộng tác trên đồ thị (`scope=recommended`) | Tính năng |
| 19 | `TrendingTopicService` | Xu hướng chỉ theo hashtag | k-means cầu + k-means++ + silhouette, tăng trưởng từng cụm | Thuật toán |
| 20 | FE `PostCardHeader` | Không hiện lý do `collaborative`/`content` | Hiện nhãn lý do đề xuất (Explainable AI) | UX |

---

## 12. Các giải thuật có thể áp dụng thêm

Các mục đã hiện thực ở các phần trên (Personalized PageRank, Label Propagation, MMR, k-means chủ đề, học trọng số xếp hạng) không còn trong danh sách này. Còn lại, sắp theo lợi ích / công sức:

| Giải thuật | Áp dụng vào | Lợi ích | Công sức |
|:---|:---|:---|:---:|
| **Louvain (tối ưu modularity)** | Thay Label Propagation khi cần cộng đồng ổn định hơn | Cộng đồng chất lượng cao hơn, có độ đo modularity để báo cáo | Trung bình |
| **Contextual bandit / Thompson Sampling** | Khám phá bài/tác giả mới có ít tương tác | Giải bài toán cold-start của bài viết | Trung bình |
| **Phân rã ma trận ngầm (ALS) tính định kỳ** | Thay CF tại chỗ khi dữ liệu lớn | Giảm chi phí theo yêu cầu | Cao |
| **LambdaMART từ nhật ký nhấp chuột** | Thay bộ học toạ độ khi có đủ log | Học tương tác phi tuyến giữa các đặc trưng | Cao (cần log) |
| **Wilson score lower bound** | Xếp hạng theo tỉ lệ thích khi ít lượt | Bài ít lượt không bị đánh giá quá cao | Thấp |
| **Cross-encoder / rerank bằng LLM** | Xếp lại top-20 kết quả tìm kiếm | Chất lượng cao hơn, tốn quota | Trung bình |
| **EWMA nhiều cửa sổ** | `HashtagTrendAnalyzer` | Mượt hơn, không "nhảy" ở ranh giới cửa sổ | Thấp |
| **Token Bucket phân tán (Redis + Lua) / GCRA** | Gateway khi chạy nhiều bản | Hạn mức đúng khi scale ngang | Trung bình |
| **W-TinyLFU (Caffeine)** | Cache vector truy vấn (đang là LRU thủ công) | Tỉ lệ trúng cache cao hơn | Thấp |
| **Bloom filter / bitmap trong Redis** | `PostSeenService` (bài đã xem) | Kiểm tra "đã xem" O(1) | Thấp |
| **pgvector `halfvec` / lượng tử hóa nhị phân + iterative scan** | `post_embeddings` | Giảm ~2× bộ nhớ chỉ mục; lọc kèm điều kiện không bị thiếu kết quả | Thấp–TB |
| **SimHash / MinHash, pHash** | Phát hiện spam trùng lặp, ảnh vi phạm đăng lại | Chặn lặp nội dung rẻ, không tốn Gemini | Trung bình |

---

## 13. Về số liệu hiệu năng

Bản tài liệu cũ có bảng đo trên máy phát triển (HNSW so với quét tuần tự ở 1 000/10 000/100 000 bài; độ trễ đăng bài 18–25 ms; tìm kiếm 180–320 ms…). **Các con số đó chưa được đo lại sau đợt tối ưu này và không có kịch bản đo kèm trong kho mã**, nên tài liệu này không lặp lại chúng như số liệu đã kiểm chứng. Những gì có thể khẳng định từ mã nguồn là **độ phức tạp** ở bảng mục 1 và các thay đổi ở mục 10.

Muốn có số liệu để đưa vào báo cáo: chạy `EXPLAIN (ANALYZE, BUFFERS)` cho hai truy vấn mục 2.4 trước/sau, và đo thời gian `FeedRankingService.compute` với dữ liệu thật (log mức DEBUG đã có sẵn các mốc thời gian cho tìm kiếm).
