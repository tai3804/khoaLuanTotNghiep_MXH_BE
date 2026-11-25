package iuh.fit.aiservice.application.service.knowledge;

import iuh.fit.aiservice.application.service.knowledge.KnowledgeMarkdownParser.Chunk;
import iuh.fit.aiservice.application.service.knowledge.KnowledgeMarkdownParser.Doc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMarkdownParserTest {

    private static String filler(int chars) {
        return "Nội dung mô tả chi tiết của mục này. ".repeat(Math.max(1, chars / 37));
    }

    private static final String SAMPLE = """
            ---
            title: Tài khoản và bảo mật
            slug: tai-khoan-va-bao-mat
            audience: user            # user | moderator | admin | developer
            tags: [đăng ký, "OTP", mật khẩu]
            updated: 2026-11-19
            ---

            # Tài khoản và bảo mật

            Tài liệu này nói về tài khoản.

            ## Cách đăng ký tài khoản

            %s

            ## Cách đổi mật khẩu

            %s

            ## Câu hỏi thường gặp

            ### Quên mật khẩu thì làm sao?

            Bấm "Quên mật khẩu" ở trang đăng nhập.

            ### OTP có hiệu lực bao lâu?

            15 phút.
            """.formatted(filler(500), filler(400));

    @Test
    void readsFrontMatterIgnoringTrailingComments() {
        Doc doc = KnowledgeMarkdownParser.parse("01-tai-khoan.md", SAMPLE);
        assertEquals("tai-khoan-va-bao-mat", doc.slug());
        assertEquals("Tài khoản và bảo mật", doc.title());
        assertEquals("user", doc.audience());
        assertEquals(List.of("đăng ký", "OTP", "mật khẩu"), doc.tags());
        assertEquals("2026-11-19", doc.updated());
    }

    @Test
    void splitsByTopicAndKeepsEveryFaqQuestionAsItsOwnChunk() {
        Doc doc = KnowledgeMarkdownParser.parse("x.md", SAMPLE);
        List<String> headings = doc.chunks().stream().map(Chunk::heading).toList();
        assertTrue(headings.contains("Cách đăng ký tài khoản"));
        assertTrue(headings.contains("Cách đổi mật khẩu"));
        // câu hỏi thường gặp rất ngắn nhưng KHÔNG được gộp lại với nhau
        assertTrue(headings.contains("Câu hỏi thường gặp > Quên mật khẩu thì làm sao?"));
        assertTrue(headings.contains("Câu hỏi thường gặp > OTP có hiệu lực bao lâu?"));
        assertEquals(2, doc.chunks().stream().filter(Chunk::faq).count());
    }

    @Test
    void textBetweenTitleAndFirstTopicBecomesTheIntroChunk() {
        Doc doc = KnowledgeMarkdownParser.parse("x.md", SAMPLE);
        Chunk intro = doc.chunks().get(0);
        assertEquals("Giới thiệu", intro.heading());
        assertTrue(intro.body().contains("Tài liệu này nói về tài khoản."));
    }

    @Test
    void mergesShortSubsectionsIntoThePreviousChunkOfTheSameTopic() {
        String md = """
                # Nhóm

                ## Vai trò trong nhóm

                %s

                ### Quản trị viên nhóm

                Có toàn quyền.

                ### Thành viên

                Chỉ đăng bài.
                """.formatted(filler(400));
        Doc doc = KnowledgeMarkdownParser.parse("nhom.md", md);
        Chunk roles = doc.chunks().stream().filter(c -> c.heading().equals("Vai trò trong nhóm")).findFirst().orElseThrow();
        assertTrue(roles.body().contains("Có toàn quyền."));
        assertTrue(roles.body().contains("Chỉ đăng bài."));
        assertFalse(doc.chunks().stream().anyMatch(c -> c.heading().contains("Thành viên")));
    }

    @Test
    void splitsAnOverlongTopicByParagraphsAndNumbersTheParts() {
        String long1 = filler(1000), long2 = filler(1000), long3 = filler(1000);
        String md = "# Dài\n\n## Chủ đề rất dài\n\n" + long1 + "\n\n" + long2 + "\n\n" + long3 + "\n";
        Doc doc = KnowledgeMarkdownParser.parse("dai.md", md);
        List<Chunk> parts = doc.chunks().stream().filter(c -> c.heading().startsWith("Chủ đề rất dài")).toList();
        assertTrue(parts.size() >= 2);
        assertEquals("Chủ đề rất dài (phần 1)", parts.get(0).heading());
        assertTrue(parts.stream().allMatch(c -> c.body().length() <= KnowledgeMarkdownParser.MAX_CHARS));
    }

    @Test
    void headingsInsideCodeFencesAreNotTopics() {
        String md = "# Mã\n\n## Ví dụ cấu hình\n\n" + filler(300) + "\n\n```\n## không phải tiêu đề\nkey: value\n```\n";
        Doc doc = KnowledgeMarkdownParser.parse("ma.md", md);
        assertEquals(1, doc.chunks().stream().filter(c -> c.heading().equals("Ví dụ cấu hình")).count());
        assertFalse(doc.chunks().stream().anyMatch(c -> c.heading().contains("không phải tiêu đề")));
    }

    @Test
    void emptySectionsAreSkippedAndMissingFrontMatterFallsBackToFileName() {
        Doc doc = KnowledgeMarkdownParser.parse("docs/05-bang-tin.md", "## Trống\n\n## Có nội dung\n\n" + filler(300));
        assertEquals("05-bang-tin", doc.slug());
        assertEquals("05-bang-tin", doc.title());
        assertEquals(1, doc.chunks().size());
        assertEquals("Có nội dung", doc.chunks().get(0).heading());
    }

    @Test
    void embeddingTextCarriesTitleAndHeadingPath() {
        Doc doc = KnowledgeMarkdownParser.parse("x.md", SAMPLE);
        Chunk faq = doc.chunks().stream().filter(Chunk::faq).findFirst().orElseThrow();
        String text = KnowledgeMarkdownParser.embeddingText(doc, faq);
        assertTrue(text.startsWith("Tài khoản và bảo mật > Câu hỏi thường gặp > Quên mật khẩu thì làm sao?\n"));
        assertTrue(text.contains("Quên mật khẩu"));
    }

    @Test
    void windowsLineEndingsAndBomAreHandled() {
        String md = "﻿---\r\ntitle: T\r\n---\r\n\r\n# T\r\n\r\n## Mục\r\n\r\n" + filler(300).replace("\n", "\r\n");
        Doc doc = KnowledgeMarkdownParser.parse("t.md", md);
        assertEquals("T", doc.title());
        assertEquals("Mục", doc.chunks().get(0).heading());
        assertFalse(doc.chunks().get(0).body().contains("\r"));
    }
}
