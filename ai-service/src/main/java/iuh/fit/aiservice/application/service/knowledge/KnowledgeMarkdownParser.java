package iuh.fit.aiservice.application.service.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Đọc một file tài liệu Markdown của kho tri thức và cắt thành các đoạn (chunk) để tạo vector.
 *
 * <p>Quy ước cắt (xem {@code knowledge/README.md}): mỗi mục {@code ##} là một chủ đề độc lập, mỗi mục {@code ###} là một đoạn
 * riêng (đặc biệt là từng câu hỏi trong "Câu hỏi thường gặp"). Phần nằm giữa tiêu đề {@code #} và mục {@code ##} đầu tiên là
 * đoạn "Giới thiệu". Đoạn quá ngắn được gộp vào đoạn liền trước cùng mục (trừ câu hỏi thường gặp, vốn phải tìm trúng riêng từng
 * câu); đoạn quá dài được cắt theo đoạn văn. Khối mã (```) được giữ nguyên, các dòng {@code #} trong đó không phải tiêu đề.
 */
public final class KnowledgeMarkdownParser {

    /** Đoạn ngắn hơn mức này (không phải câu hỏi thường gặp) được gộp vào đoạn trước cùng mục. */
    public static final int MIN_CHARS = 250;
    /** Đoạn dài hơn mức này bị cắt theo đoạn văn. */
    public static final int MAX_CHARS = 1800;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final String INTRO_HEADING = "Giới thiệu";

    private KnowledgeMarkdownParser() {
    }

    /** Một đoạn sẵn sàng tạo vector. {@code heading} là đường dẫn "Mục > Mục con"; {@code body} không chứa tiêu đề. */
    public record Chunk(String heading, String body, boolean faq) {
    }

    public record Doc(String slug, String title, String audience, List<String> tags, String updated, List<Chunk> chunks) {
    }

    /** Văn bản đưa vào mô hình embedding: gắn đường dẫn để đoạn mang đủ ngữ cảnh (đoạn tự nó có thể không nhắc tên chủ đề). */
    public static String embeddingText(Doc doc, Chunk chunk) {
        return doc.title() + " > " + chunk.heading() + "\n" + chunk.body();
    }

    public static Doc parse(String fileName, String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("﻿")) text = text.substring(1);
        String[] lines = text.split("\n", -1);

        int i = 0;
        Map<String, String> meta = new LinkedHashMap<>();
        while (i < lines.length && lines[i].isBlank()) i++;
        if (i < lines.length && lines[i].trim().equals("---")) {
            int end = -1;
            for (int j = i + 1; j < lines.length; j++) {
                if (lines[j].trim().equals("---")) {
                    end = j;
                    break;
                }
            }
            if (end > 0) {
                for (int j = i + 1; j < end; j++) {
                    int colon = lines[j].indexOf(':');
                    if (colon <= 0) continue;
                    meta.put(lines[j].substring(0, colon).trim().toLowerCase(Locale.ROOT), stripComment(lines[j].substring(colon + 1)).trim());
                }
                i = end + 1;
            }
        }

        String title = null;
        List<Section> sections = new ArrayList<>();
        Section current = new Section(null, null);
        boolean inFence = false;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
                current.lines.add(line);
                continue;
            }
            if (!inFence) {
                if (line.startsWith("# ") && title == null) {
                    title = line.substring(2).trim();
                    continue;
                }
                if (line.startsWith("### ")) {
                    sections.add(current);
                    current = new Section(current.h2, line.substring(4).trim());
                    continue;
                }
                if (line.startsWith("## ")) {
                    sections.add(current);
                    current = new Section(line.substring(3).trim(), null);
                    continue;
                }
            }
            current.lines.add(line);
        }
        sections.add(current);

        String slug = meta.getOrDefault("slug", baseName(fileName));
        String docTitle = firstNonBlank(meta.get("title"), title, slug);
        List<Chunk> chunks = toChunks(sections, docTitle);
        return new Doc(slug, docTitle, meta.getOrDefault("audience", "user").toLowerCase(Locale.ROOT), parseTags(meta.get("tags")),
                meta.getOrDefault("updated", ""), chunks);
    }

    private static final class Section {
        final String h2;
        final String h3;
        final List<String> lines = new ArrayList<>();

        Section(String h2, String h3) {
            this.h2 = h2;
            this.h3 = h3;
        }

        String heading() {
            if (h2 == null && h3 == null) return INTRO_HEADING;
            if (h3 == null) return h2;
            return h2 == null ? h3 : h2 + " > " + h3;
        }

        String body() {
            return String.join("\n", lines).strip();
        }

        boolean faq() {
            return h2 != null && fold(h2).startsWith("cau hoi thuong gap");
        }
    }

    private static List<Chunk> toChunks(List<Section> sections, String docTitle) {
        List<Chunk> merged = new ArrayList<>();
        String lastH2 = null;
        boolean lastFaq = false;
        for (Section s : sections) {
            String body = s.body();
            if (body.isEmpty()) continue;
            boolean faq = s.faq();
            boolean sameTopic = !merged.isEmpty() && java.util.Objects.equals(lastH2, s.h2);
            if (body.length() < MIN_CHARS && !faq && !lastFaq && sameTopic) {
                Chunk prev = merged.remove(merged.size() - 1);
                String label = s.h3 != null ? "**" + s.h3 + "**\n" : "";
                merged.add(new Chunk(prev.heading(), prev.body() + "\n\n" + label + body, false));
            } else {
                merged.add(new Chunk(s.heading(), body, faq));
            }
            lastH2 = s.h2;
            lastFaq = faq;
        }

        List<Chunk> out = new ArrayList<>();
        for (Chunk c : merged) {
            if (c.body().length() <= MAX_CHARS) {
                out.add(c);
                continue;
            }
            List<String> parts = splitLong(c.body());
            for (int p = 0; p < parts.size(); p++) {
                String heading = parts.size() == 1 ? c.heading() : c.heading() + " (phần " + (p + 1) + ")";
                out.add(new Chunk(heading, parts.get(p), c.faq()));
            }
        }
        return out;
    }

    /** Cắt theo đoạn văn (dòng trống); đoạn văn đơn lẻ vẫn quá dài thì cắt theo dòng. */
    static List<String> splitLong(String body) {
        List<String> units = new ArrayList<>();
        for (String para : body.split("\n\\s*\n")) {
            if (para.length() <= MAX_CHARS) {
                units.add(para.strip());
            } else {
                for (String line : para.split("\n")) units.add(line.strip());
            }
        }
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String u : units) {
            if (u.isEmpty()) continue;
            if (sb.length() > 0 && sb.length() + u.length() + 2 > MAX_CHARS) {
                parts.add(sb.toString());
                sb.setLength(0);
            }
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(u);
        }
        if (sb.length() > 0) parts.add(sb.toString());
        return parts;
    }

    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
        List<String> tags = new ArrayList<>();
        for (String piece : t.split(",")) {
            String tag = piece.trim().replaceAll("^[\"']|[\"']$", "");
            if (!tag.isEmpty()) tags.add(tag);
        }
        return tags;
    }

    private static String stripComment(String value) {
        int hash = value.indexOf(" #");
        return hash >= 0 ? value.substring(0, hash) : value;
    }

    private static String baseName(String fileName) {
        String n = fileName == null ? "tai-lieu" : fileName;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        return n.endsWith(".md") ? n.substring(0, n.length() - 3) : n;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return DIACRITICS.matcher(n).replaceAll("").replace('đ', 'd').trim();
    }
}
