package iuh.fit.aiservice.application.service.knowledge;

import iuh.fit.aiservice.application.service.knowledge.KnowledgeMarkdownParser.Chunk;
import iuh.fit.aiservice.application.service.knowledge.KnowledgeMarkdownParser.Doc;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bảo vệ chính bộ tài liệu trong {@code src/main/resources/knowledge}: ai sửa tài liệu làm hỏng quy ước (xem README.md của thư mục đó)
 * hoặc lỡ dán bí mật vào sẽ bị báo ngay khi chạy test, thay vì phát hiện qua câu trả lời sai của trợ lý.
 */
class KnowledgeDocsTest {

    private static final Set<String> AUDIENCES = Set.of("user", "moderator", "admin", "developer");
    private static final Pattern SECRET = Pattern.compile(
            "AIza[0-9A-Za-z_-]{20,}|BEGIN (RSA |EC )?(PRIVATE|PUBLIC) KEY|(?i:api[_-]?key|password|secret)\\s*[:=]\\s*[A-Za-z0-9/+_-]{12,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\\.[a-z]{2,}");

    private record Loaded(String file, String raw, Doc doc) {
    }

    private static List<Loaded> load() throws Exception {
        List<Loaded> out = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:knowledge/*.md")) {
            String name = r.getFilename();
            if (name == null || name.equalsIgnoreCase("README.md") || name.startsWith("_")) continue;
            String raw = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            out.add(new Loaded(name, raw, KnowledgeMarkdownParser.parse(name, raw)));
        }
        return out;
    }

    @Test
    void thereAreDocumentsAndEachHasValidFrontMatter() throws Exception {
        List<Loaded> docs = load();
        assertTrue(docs.size() >= 10, "kho tri thức phải có tài liệu, tìm thấy " + docs.size());
        Set<String> slugs = new HashSet<>();
        for (Loaded l : docs) {
            Doc d = l.doc();
            assertTrue(l.raw().stripLeading().startsWith("---"), l.file() + ": thiếu front matter");
            assertFalse(d.title().isBlank(), l.file() + ": thiếu title");
            assertTrue(d.slug().matches("[a-z0-9]+(-[a-z0-9]+)*"), l.file() + ": slug không hợp lệ: " + d.slug());
            assertTrue(slugs.add(d.slug()), l.file() + ": slug trùng " + d.slug());
            assertTrue(AUDIENCES.contains(d.audience()), l.file() + ": audience không hợp lệ: " + d.audience());
            assertFalse(d.tags().isEmpty(), l.file() + ": thiếu tags");
            assertTrue(d.updated().matches("\\d{4}-\\d{2}-\\d{2}"), l.file() + ": updated phải dạng YYYY-MM-DD");
        }
    }

    @Test
    void everyDocumentHasFaqQuestionsAndChunksOfSaneSize() throws Exception {
        for (Loaded l : load()) {
            List<Chunk> chunks = l.doc().chunks();
            long faq = chunks.stream().filter(Chunk::faq).count();
            assertTrue(faq >= 6, l.file() + ": cần ít nhất 6 câu hỏi thường gặp, có " + faq);
            Set<String> headings = new HashSet<>();
            for (Chunk c : chunks) {
                assertFalse(c.body().isBlank(), l.file() + ": đoạn rỗng " + c.heading());
                assertTrue(c.body().length() <= KnowledgeMarkdownParser.MAX_CHARS,
                        l.file() + ": đoạn quá dài (" + c.body().length() + ") " + c.heading());
                assertTrue(headings.add(c.heading()), l.file() + ": tiêu đề trùng " + c.heading());
            }
            long topics = chunks.stream().filter(c -> !c.faq()).count();
            assertTrue(topics >= 5, l.file() + ": quá ít chủ đề (" + topics + ")");
        }
    }

    @Test
    void faqQuestionsAreQuestionsWithAnAnswer() throws Exception {
        for (Loaded l : load()) {
            for (Chunk c : l.doc().chunks()) {
                if (!c.faq()) continue;
                String question = c.heading().substring(c.heading().indexOf('>') + 1).trim();
                assertTrue(question.endsWith("?"), l.file() + ": câu hỏi phải kết thúc bằng dấu ? : " + question);
                assertTrue(c.body().length() >= 40, l.file() + ": câu trả lời quá ngắn cho \"" + question + "\"");
            }
        }
    }

    @Test
    void noSecretsOrPersonalDataInDocuments() throws Exception {
        for (Loaded l : load()) {
            var m = SECRET.matcher(l.raw());
            assertFalse(m.find(), l.file() + ": có thể chứa bí mật/email: " + (m.hitEnd() ? "" : m.group()));
        }
    }

    @Test
    void totalChunkCountIsReasonableForOneBatchRun() throws Exception {
        int chunks = load().stream().mapToInt(l -> l.doc().chunks().size()).sum();
        assertTrue(chunks >= 100 && chunks <= 600, "số đoạn bất thường: " + chunks);
        assertEquals(chunks, load().stream().mapToInt(l -> l.doc().chunks().size()).sum());
    }
}
