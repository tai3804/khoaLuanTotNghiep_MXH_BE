package iuh.fit.postservice.infrastructure.persistence.search;

import iuh.fit.postservice.infrastructure.persistence.config.PostSearchSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Truy xuất từ khoá trên chỉ mục đảo (GIN trên posts.search_tsv) và thống kê corpus cho BM25.
 *
 * <p>Chỉ nhận token đã qua {@code SearchTextAnalyzer} (chỉ gồm [a-z0-9]) nên ghép chuỗi vào tsquery an toàn; vẫn truyền qua
 * tham số JDBC, không nối vào SQL.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostLexicalIndex {

    private static final String LIVE =
            "coalesce(p.is_deleted, false) = false AND p.status = 'PUBLISHED' AND coalesce(p.is_archived, false) = false";
    private static final long STATS_TTL_MS = 5 * 60_000L;

    private final JdbcTemplate jdbcTemplate;
    private final PostSearchSupport postSearchSupport;

    private record Cached<T>(T value, long at) {
    }

    /** N (tổng số bài đang hiển thị) và độ dài trung bình (số token) dùng cho BM25. */
    public record CorpusStats(long totalDocs, double avgDocLength) {
    }

    private volatile Cached<CorpusStats> corpus;
    private final ConcurrentHashMap<String, Cached<Long>> docFreqCache = new ConcurrentHashMap<>();

    public boolean isAvailable() {
        return postSearchSupport.isFullTextAvailable();
    }

    /** "a | b | c:*" — OR để có độ phủ cao, BM25 sẽ xếp bài khớp nhiều từ lên trước. */
    public static String toTsQuery(List<String> terms, boolean prefixLast) {
        List<String> parts = new ArrayList<>(terms.size());
        for (int i = 0; i < terms.size(); i++) {
            boolean prefix = prefixLast && i == terms.size() - 1;
            parts.add(terms.get(i) + (prefix ? ":*" : ""));
        }
        return String.join(" | ", parts);
    }

    /**
     * Danh sách id bài khớp (đã sắp theo ts_rank_cd giảm dần). {@code authorIds != null} giới hạn trong bài của các tác giả đó
     * (toán tử "friends-of" của Unicorn: giao posting list của từ khoá với tập tác giả trong đồ thị xã hội).
     */
    public List<UUID> match(String tsQuery, int limit, Collection<UUID> authorIds) {
        if (tsQuery == null || tsQuery.isBlank() || !isAvailable()) return Collections.emptyList();
        if (authorIds != null && authorIds.isEmpty()) return Collections.emptyList();
        try {
            String authorFilter = authorIds == null ? "" : " AND p.author_id = ANY(?)";
            String sql = "WITH q AS (SELECT to_tsquery('simple', ?) AS tsq) "
                    + "SELECT p.id FROM posts p, q WHERE p.search_tsv @@ q.tsq AND " + LIVE + authorFilter
                    + " ORDER BY ts_rank_cd(p.search_tsv, q.tsq) DESC, p.created_at DESC LIMIT ?";
            return jdbcTemplate.query(con -> {
                var ps = con.prepareStatement(sql);
                int i = 1;
                ps.setString(i++, tsQuery);
                if (authorIds != null) {
                    ps.setArray(i++, con.createArrayOf("uuid", authorIds.toArray()));
                }
                ps.setInt(i, Math.max(1, Math.min(limit, 500)));
                return ps;
            }, (rs, n) -> UUID.fromString(rs.getString(1)));
        } catch (Exception e) {
            log.warn("Lexical retrieval failed for '{}': {}", tsQuery, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** df của một từ khoá (có thể là tiền tố), lấy từ chỉ mục GIN, cache 5 phút. */
    public long docFrequency(String term, boolean prefix) {
        String key = term + (prefix ? ":*" : "");
        Cached<Long> hit = docFreqCache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at() < STATS_TTL_MS) return hit.value();
        try {
            Long df = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM posts p WHERE p.search_tsv @@ to_tsquery('simple', ?) AND " + LIVE,
                    Long.class, key);
            long value = df == null ? 0 : df;
            if (docFreqCache.size() > 5000) docFreqCache.clear();
            docFreqCache.put(key, new Cached<>(value, now));
            return value;
        } catch (Exception e) {
            log.warn("Could not read document frequency of '{}': {}", key, e.getMessage());
            return 0;
        }
    }

    public CorpusStats corpusStats() {
        Cached<CorpusStats> c = corpus;
        long now = System.currentTimeMillis();
        if (c != null && now - c.at() < STATS_TTL_MS) return c.value();
        try {
            return jdbcTemplate.query(
                    "SELECT count(*), coalesce(avg(coalesce(array_length(regexp_split_to_array(trim(kltn_fold(p.content)), '[^a-z0-9]+'), 1), 0)), 0) "
                            + "FROM posts p WHERE " + LIVE,
                    rs -> {
                        rs.next();
                        CorpusStats s = new CorpusStats(rs.getLong(1), Math.max(rs.getDouble(2), 1.0));
                        corpus = new Cached<>(s, now);
                        return s;
                    });
        } catch (Exception e) {
            log.warn("Could not read corpus statistics: {}", e.getMessage());
            return c != null ? c.value() : new CorpusStats(0, 1.0);
        }
    }
}
