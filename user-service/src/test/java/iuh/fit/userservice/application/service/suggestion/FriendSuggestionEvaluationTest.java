package iuh.fit.userservice.application.service.suggestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đánh giá offline (hold-out) chất lượng xếp hạng "bạn chung" của {@link FriendSuggestionScorer} trên một đồ thị bạn bè thật.
 * Chỉ chạy khi đặt biến môi trường {@code SUGGEST_EVAL_EDGES} (nên không làm chậm các lần chạy test thường).
 *
 * <h3>Giao thức</h3>
 * Với mỗi người dùng thử nghiệm u (bậc >= {@value #MIN_DEGREE}): ẩn ngẫu nhiên {@value #HIDE_FRACTION_PERCENT}% quan hệ bạn bè của u,
 * coi các quan hệ bị ẩn là "đáp án". Ứng viên là những người cách u đúng 2 bước trong đồ thị đã ẩn (cùng cách
 * {@link FriendSuggestionEngine} tìm bạn của bạn). Mỗi cách chấm điểm xếp hạng ứng viên; ta đo xem đáp án nằm ở đâu trong
 * danh sách. Chỉ quan hệ của chính u bị ẩn nên mỗi người dùng là một phép thử độc lập, giống tình huống thật
 * ("hôm nay u có thể kết bạn với ai?"). Nhãn nút được xáo ngẫu nhiên có hạt giống để việc phá hòa theo id (như Engine:
 * điểm bằng nhau thì xếp theo userId) không vô tình trùng với thứ tự cộng đồng của tập dữ liệu.
 *
 * <h3>Phạm vi</h3>
 * Tập dữ liệu công khai chỉ có cạnh bạn bè, không có nhóm / theo dõi / hồ sơ. Vì vậy bài đánh giá này chỉ kiểm chứng phần
 * <b>bạn chung</b> (và độ phổ biến, nhiễu theo ngày vốn có trong Scorer). Các tín hiệu nhóm, theo dõi, hồ sơ KHÔNG được đo ở đây.
 *
 * <h3>Cách chạy</h3>
 * Tải {@code facebook_combined.txt.gz} của SNAP ego-Facebook (McAuley &amp; Leskovec, NIPS 2012), giải nén, rồi:
 * <pre>
 * SUGGEST_EVAL_EDGES=/đường/dẫn/facebook_combined.txt SUGGEST_EVAL_OUT=/đường/dẫn/ket_qua.json \
 *   mvn test -Dtest=FriendSuggestionEvaluationTest
 * </pre>
 * Tùy chọn: {@code SUGGEST_EVAL_USERS} (mặc định 1500), {@code SUGGEST_EVAL_SEEDS} (mặc định "1,2,3"; hạt giống đầu tiên là
 * lần đo chính, các hạt còn lại để kiểm tra độ ổn định).
 */
@EnabledIfEnvironmentVariable(named = "SUGGEST_EVAL_EDGES", matches = ".+")
class FriendSuggestionEvaluationTest {

    static final int K = 10;
    static final double HIDE_FRACTION = 0.2;
    static final int HIDE_FRACTION_PERCENT = 20;
    static final int MIN_DEGREE = 10;
    static final long RELABEL_SEED = 20261114L;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    // Thứ tự cố định của các cách chấm điểm trong báo cáo
    static final String[] VARIANTS = {
            "random",
            "common_neighbors",
            "jaccard",
            "adamic_adar_original",
            "adamic_adar_plus2",
            "mutual_hard_cap",
            "mutual_soft_saturation",
            "soft_plus_popularity",
            "soft_plus_jitter",
            "project_scorer"
    };
    static final String[] METRICS = {"recall_at_10", "precision_at_10", "hit_at_10", "ndcg_at_10", "mrr"};

    private int n;
    private int[][] adj;
    private long edgeCount;

    // ------------------------------------------------------------------------------------------------ đồ thị

    /** Đọc danh sách cạnh vô hướng, gộp cạnh trùng / khuyên, gán lại nhãn nút theo hoán vị ngẫu nhiên có hạt giống. */
    private void loadGraph(Path file) throws IOException {
        Map<String, Integer> index = new HashMap<>();
        List<int[]> raw = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int a = index.computeIfAbsent(p[0], k -> index.size());
                int b = index.computeIfAbsent(p[1], k -> index.size());
                if (a != b) raw.add(new int[]{a, b});
            }
        }
        n = index.size();
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        Random rnd = new Random(RELABEL_SEED);
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        List<java.util.Set<Integer>> sets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) sets.add(new java.util.TreeSet<>());
        for (int[] e : raw) {
            sets.get(perm[e[0]]).add(perm[e[1]]);
            sets.get(perm[e[1]]).add(perm[e[0]]);
        }
        adj = new int[n][];
        long degreeSum = 0;
        for (int i = 0; i < n; i++) {
            adj[i] = sets.get(i).stream().mapToInt(Integer::intValue).toArray();
            degreeSum += adj[i].length;
        }
        edgeCount = degreeSum / 2;
    }

    private static UUID uuid(int node) {
        return new UUID(0L, node);
    }

    // --------------------------------------------------------------------------------- một phép thử (một người dùng)

    /** Kết quả của một người dùng: số đo của từng cách chấm điểm + thống kê "chạm trần". */
    private static final class UserResult {
        final double[][] metrics = new double[VARIANTS.length][METRICS.length];
        double coverage;
        int hidden;
        int candidates;
        int saturated;
    }

    private UserResult evaluateUser(int u, long seed) {
        Random rnd = new Random(seed * 1_000_003L + u);
        int[] nb = adj[u];
        int h = Math.max(1, (int) Math.round(HIDE_FRACTION * nb.length));
        List<Integer> pool = new ArrayList<>(nb.length);
        for (int v : nb) pool.add(v);
        Collections.shuffle(pool, rnd);

        boolean[] hidden = new boolean[n];
        for (int i = 0; i < h; i++) hidden[pool.get(i)] = true;
        boolean[] isFriend = new boolean[n];
        int friendTotal = 0;
        for (int v : nb) {
            if (!hidden[v]) { isFriend[v] = true; friendTotal++; }
        }

        int[] cnt = new int[n];
        double[] aaOriginal = new double[n];
        double[] aaPlus2 = new double[n];
        @SuppressWarnings("unchecked")
        List<Integer>[] degrees = new List[n];
        List<Integer> touched = new ArrayList<>();
        for (int v : nb) {
            if (!isFriend[v]) continue;
            int dv = adj[v].length;
            if (dv < 2) continue; // v chỉ quen u: không dẫn tới ai
            double wOriginal = 1.0 / Math.log(dv);
            double wPlus2 = 1.0 / Math.log(dv + 2.0);
            for (int c : adj[v]) {
                if (c == u || isFriend[c]) continue;
                if (cnt[c] == 0) touched.add(c);
                cnt[c]++;
                aaOriginal[c] += wOriginal;
                aaPlus2[c] += wPlus2;
                if (degrees[c] == null) degrees[c] = new ArrayList<>();
                degrees[c].add(dv);
            }
        }
        Collections.sort(touched);
        int m = touched.size();
        UserResult res = new UserResult();
        res.hidden = h;
        res.candidates = m;

        int reachable = 0;
        for (int c : touched) if (hidden[c]) reachable++;
        res.coverage = (double) reachable / h;
        if (m == 0) return res; // mọi số đo = 0

        double[][] score = new double[VARIANTS.length][m];
        for (int i = 0; i < m; i++) {
            int c = touched.get(i);
            long friendCount = adj[c].length - (hidden[c] ? 1 : 0);
            double aa2 = aaPlus2[c];
            score[0][i] = rnd.nextDouble();
            score[1][i] = cnt[c];
            score[2][i] = (double) cnt[c] / (friendTotal + friendCount - cnt[c]);
            score[3][i] = aaOriginal[c];
            score[4][i] = aa2;
            score[5][i] = FriendSuggestionScorer.MUTUAL_BASE
                    + Math.min(FriendSuggestionScorer.MUTUAL_CAP, FriendSuggestionScorer.MUTUAL_WEIGHT * aa2);
            score[6][i] = FriendSuggestionScorer.mutualScore(aa2);
            // Ablation: thêm riêng từng tín hiệu phụ của Scorer (cùng công thức với Scorer) lên điểm bạn chung thuần
            double popularity = Math.min(FriendSuggestionScorer.POPULARITY_CAP, 0.15 * Math.log(1 + Math.max(0, friendCount)));
            long epochDay = NOW.toLocalDate().toEpochDay();
            double jitter = Math.floorMod(Objects.hash(uuid(u), uuid(c), epochDay), 1000) / 1000.0 * FriendSuggestionScorer.JITTER_MAX;
            score[7][i] = score[6][i] + popularity;
            score[8][i] = score[6][i] + jitter;
            FriendSuggestionScorer.Signals s = new FriendSuggestionScorer.Signals(
                    degrees[c], 0, 0.0, false, false, null, null, null, false, null, null, friendCount);
            score[9][i] = FriendSuggestionScorer.score(uuid(u), uuid(c), s, NOW).score();
            if (FriendSuggestionScorer.MUTUAL_WEIGHT * aa2 >= FriendSuggestionScorer.MUTUAL_CAP) res.saturated++;
        }

        double idcg = 0;
        for (int i = 0; i < Math.min(h, K); i++) idcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));

        for (int v = 0; v < VARIANTS.length; v++) {
            final double[] sc = score[v];
            Integer[] order = new Integer[m];
            for (int i = 0; i < m; i++) order[i] = i;
            // Giống Engine: điểm giảm dần, bằng nhau thì theo id ứng viên tăng dần (touched đã sắp tăng dần theo id)
            Arrays.sort(order, (a, b) -> {
                int cmp = Double.compare(sc[b], sc[a]);
                return cmp != 0 ? cmp : Integer.compare(a, b);
            });
            int hits = 0;
            double dcg = 0, rr = 0;
            for (int i = 0; i < m; i++) {
                int c = touched.get(order[i]);
                if (hidden[c]) {
                    if (rr == 0) rr = 1.0 / (i + 1);
                    if (i < K) { hits++; dcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0)); }
                }
                if (i >= K && rr > 0) break;
            }
            res.metrics[v][0] = (double) hits / h;
            res.metrics[v][1] = (double) hits / K;
            res.metrics[v][2] = hits > 0 ? 1 : 0;
            res.metrics[v][3] = idcg > 0 ? dcg / idcg : 0;
            res.metrics[v][4] = rr;
        }
        return res;
    }

    // -------------------------------------------------------------------------------------------- thống kê

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    private static double ci95(double[] x) {
        if (x.length < 2) return 0;
        double mu = mean(x), ss = 0;
        for (double v : x) ss += (v - mu) * (v - mu);
        return 1.96 * Math.sqrt(ss / (x.length - 1)) / Math.sqrt(x.length);
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    // -------------------------------------------------------------------------------------------------- chạy

    @Test
    void evaluate() throws IOException {
        Path edges = Path.of(System.getenv("SUGGEST_EVAL_EDGES"));
        Path out = Path.of(System.getenv().getOrDefault("SUGGEST_EVAL_OUT", "suggestion-eval.json"));
        int wantedUsers = Integer.parseInt(System.getenv().getOrDefault("SUGGEST_EVAL_USERS", "1500"));
        long[] seeds = Arrays.stream(System.getenv().getOrDefault("SUGGEST_EVAL_SEEDS", "1,2,3").split(","))
                .map(String::trim).mapToLong(Long::parseLong).toArray();

        loadGraph(edges);
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < n; i++) if (adj[i].length >= MIN_DEGREE) eligible.add(i);
        double avgDegree = 2.0 * edgeCount / n;

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"graph\": {\"nodes\": ").append(n).append(", \"edges\": ").append(edgeCount)
                .append(", \"avg_degree\": ").append(num(avgDegree))
                .append(", \"eligible_users\": ").append(eligible.size()).append("},\n");
        json.append("  \"protocol\": {\"hide_fraction\": ").append(HIDE_FRACTION).append(", \"min_degree\": ").append(MIN_DEGREE)
                .append(", \"k\": ").append(K).append(", \"requested_users\": ").append(wantedUsers)
                .append(", \"mutual_base\": ").append(FriendSuggestionScorer.MUTUAL_BASE)
                .append(", \"mutual_weight\": ").append(FriendSuggestionScorer.MUTUAL_WEIGHT)
                .append(", \"mutual_cap\": ").append(FriendSuggestionScorer.MUTUAL_CAP).append("},\n");
        json.append("  \"variants\": [");
        for (int i = 0; i < VARIANTS.length; i++) json.append(i > 0 ? ", " : "").append('"').append(VARIANTS[i]).append('"');
        json.append("],\n  \"runs\": [\n");

        for (int si = 0; si < seeds.length; si++) {
            long seed = seeds[si];
            List<Integer> sample = new ArrayList<>(eligible);
            Collections.shuffle(sample, new Random(seed));
            if (sample.size() > wantedUsers) sample = new ArrayList<>(sample.subList(0, wantedUsers));
            int nUsers = sample.size();

            double[][][] perUser = new double[VARIANTS.length][METRICS.length][nUsers];
            double[] coverage = new double[nUsers];
            double[] hiddenCount = new double[nUsers];
            double[] candCount = new double[nUsers];
            double[] satCount = new double[nUsers];
            double[] satTop = new double[nUsers];
            for (int ui = 0; ui < nUsers; ui++) {
                UserResult r = evaluateUser(sample.get(ui), seed);
                for (int v = 0; v < VARIANTS.length; v++)
                    for (int mt = 0; mt < METRICS.length; mt++) perUser[v][mt][ui] = r.metrics[v][mt];
                coverage[ui] = r.coverage;
                hiddenCount[ui] = r.hidden;
                candCount[ui] = r.candidates;
                satCount[ui] = r.saturated;
                satTop[ui] = r.saturated >= K ? 1 : 0;
            }

            json.append("    {\"seed\": ").append(seed).append(", \"users\": ").append(nUsers)
                    .append(", \"mean_hidden_edges\": ").append(num(mean(hiddenCount)))
                    .append(", \"mean_candidates\": ").append(num(mean(candCount)))
                    .append(", \"coverage_upper_bound\": ").append(num(mean(coverage)))
                    .append(", \"mean_saturated_candidates\": ").append(num(mean(satCount)))
                    .append(", \"share_users_top10_all_tied_under_hard_cap\": ").append(num(mean(satTop)))
                    .append(",\n      \"metrics\": {\n");
            for (int v = 0; v < VARIANTS.length; v++) {
                json.append("        \"").append(VARIANTS[v]).append("\": {");
                for (int mt = 0; mt < METRICS.length; mt++) {
                    json.append(mt > 0 ? ", " : "").append('"').append(METRICS[mt]).append("\": [")
                            .append(num(mean(perUser[v][mt]))).append(", ").append(num(ci95(perUser[v][mt]))).append(']');
                }
                json.append('}').append(v < VARIANTS.length - 1 ? "," : "").append('\n');
            }
            json.append("      },\n      \"paired_differences\": [\n");
            String[][] pairs = {
                    {"mutual_soft_saturation", "mutual_hard_cap"},
                    {"project_scorer", "mutual_hard_cap"},
                    {"adamic_adar_original", "common_neighbors"},
                    {"adamic_adar_plus2", "adamic_adar_original"},
                    {"adamic_adar_plus2", "common_neighbors"},
                    {"project_scorer", "common_neighbors"},
                    {"project_scorer", "random"},
                    {"soft_plus_popularity", "mutual_soft_saturation"},
                    {"soft_plus_jitter", "mutual_soft_saturation"},
                    {"project_scorer", "mutual_soft_saturation"}
            };
            for (int pi = 0; pi < pairs.length; pi++) {
                int a = Arrays.asList(VARIANTS).indexOf(pairs[pi][0]);
                int b = Arrays.asList(VARIANTS).indexOf(pairs[pi][1]);
                json.append("        {\"a\": \"").append(pairs[pi][0]).append("\", \"b\": \"").append(pairs[pi][1]).append("\"");
                for (int mt = 0; mt < METRICS.length; mt++) {
                    double[] d = new double[nUsers];
                    for (int ui = 0; ui < nUsers; ui++) d[ui] = perUser[a][mt][ui] - perUser[b][mt][ui];
                    json.append(", \"").append(METRICS[mt]).append("\": [").append(num(mean(d))).append(", ").append(num(ci95(d))).append(']');
                }
                json.append('}').append(pi < pairs.length - 1 ? "," : "").append('\n');
            }
            json.append("      ]\n    }").append(si < seeds.length - 1 ? "," : "").append('\n');
        }
        json.append("  ]\n}\n");

        Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        assertTrue(Files.size(out) > 0);
    }
}
