package iuh.fit.postservice.application.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagAndHashtagHelper {

    // Matches hashtags: #CongNghe, #kltn_2026, #khoa_luan, #TiếngViệt
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_]+)");

    // Matches markdown-style mentions: @[Alice Smith](d5b6f15b-d11f-4b07-8d7e-3047e87eaea8)
    private static final Pattern MARKDOWN_MENTION_PATTERN = Pattern.compile("@\\[([^\\]]+)\\]\\(([a-fA-F0-9\\-]{36})\\)");

    // Matches direct UUID mentions: @d5b6f15b-d11f-4b07-8d7e-3047e87eaea8
    private static final Pattern UUID_MENTION_PATTERN = Pattern.compile("@([a-fA-F0-9\\-]{36})");

    private TagAndHashtagHelper() {}

    /**
     * Extracts hashtags from text content without the '#' prefix, normalized to lowercase.
     */
    public static Set<String> extractHashtags(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> hashtags = new HashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            String tag = matcher.group(1).trim().toLowerCase();
            if (!tag.isEmpty()) {
                hashtags.add(tag);
            }
        }
        return hashtags;
    }

    /**
     * Extracts mentioned user UUIDs from text content.
     */
    public static Set<UUID> extractMentions(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptySet();
        }
        Set<UUID> mentions = new HashSet<>();

        // 1. Check markdown mentions: @[Name](uuid)
        Matcher mdMatcher = MARKDOWN_MENTION_PATTERN.matcher(content);
        while (mdMatcher.find()) {
            try {
                mentions.add(UUID.fromString(mdMatcher.group(2)));
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. Check direct UUID mentions: @uuid
        Matcher uuidMatcher = UUID_MENTION_PATTERN.matcher(content);
        while (uuidMatcher.find()) {
            try {
                mentions.add(UUID.fromString(uuidMatcher.group(1)));
            } catch (IllegalArgumentException ignored) {}
        }

        return mentions;
    }
}
