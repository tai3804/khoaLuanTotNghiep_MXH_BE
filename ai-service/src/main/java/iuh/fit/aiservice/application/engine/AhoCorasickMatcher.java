package iuh.fit.aiservice.application.engine;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Automaton Aho–Corasick (Aho &amp; Corasick, 1975) tìm đồng thời mọi từ khoá trong một văn bản bằng MỘT lượt quét.
 *
 * <h3>Vì sao thay cho "lặp từng từ khoá + regex"</h3>
 * Cách cũ biên dịch một {@code Pattern} cho mỗi từ khoá ở mỗi lần quét nên tốn O(K · (N + chi phí biên dịch)) với K từ khoá,
 * N ký tự. Automaton dựng một lần (O(Σ|từ khoá|)) rồi mỗi lần quét chỉ tốn O(N + số lần khớp), không phụ thuộc K.
 *
 * <h3>Cấu trúc</h3>
 * <ul>
 *   <li>Trie các từ khoá; mỗi nút có <i>liên kết thất bại</i> (fail) trỏ tới hậu tố dài nhất của chuỗi đang xét cũng là tiền tố
 *       của một từ khoá khác, dựng bằng BFS;</li>
 *   <li>Mỗi nút có thêm <i>liên kết từ điển</i> (dict) tới nút gần nhất trên chuỗi fail kết thúc một từ khoá, để liệt kê mọi từ
 *       khoá là hậu tố của vị trí hiện tại ("địt mẹ mày", "địt mẹ", "mẹ mày" cùng kết thúc tại một chỗ) mà không phải đi cả chuỗi fail.</li>
 * </ul>
 *
 * <h3>Ranh giới từ</h3>
 * Giống lookaround {@code (?<![\p{L}\p{Nd}])từ(?![\p{L}\p{Nd}])} của cách cũ: một lần khớp chỉ được tính khi ký tự ngay trước và
 * ngay sau không phải chữ cái hay chữ số Unicode. Nhờ đó "điểm" không bị dính "đĩ" và "buổi" không bị dính "buồi".
 *
 * <p>Đối tượng bất biến sau khi dựng nên dùng chung giữa các luồng mà không cần khoá.
 */
public final class AhoCorasickMatcher {

    private static final class Node {
        Map<Character, Node> next;
        Node fail;
        Node dict;
        /** Từ khoá kết thúc tại nút này (null nếu không có). Trie nên mỗi nút có nhiều nhất một từ khoá. */
        String key;

        Node child(char c) {
            return next == null ? null : next.get(c);
        }
    }

    private final Node root = new Node();
    private final int size;

    public AhoCorasickMatcher(Collection<String> keys) {
        int count = 0;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            Node node = root;
            for (int i = 0; i < key.length(); i++) {
                if (node.next == null) node.next = new HashMap<>(4);
                node = node.next.computeIfAbsent(key.charAt(i), c -> new Node());
            }
            if (node.key == null) count++;
            node.key = key;
        }
        this.size = count;
        buildLinks();
    }

    /** Số từ khoá khác nhau trong automaton. */
    public int size() {
        return size;
    }

    private void buildLinks() {
        ArrayDeque<Node> queue = new ArrayDeque<>();
        root.fail = root;
        if (root.next != null) {
            for (Node child : root.next.values()) {
                child.fail = root;
                queue.add(child);
            }
        }
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.next == null) continue;
            for (Map.Entry<Character, Node> e : node.next.entrySet()) {
                char c = e.getKey();
                Node child = e.getValue();
                Node f = node.fail;
                while (f != root && f.child(c) == null) f = f.fail;
                Node target = f.child(c);
                child.fail = (target != null && target != child) ? target : root;
                child.dict = child.fail.key != null ? child.fail : child.fail.dict;
                queue.add(child);
            }
        }
    }

    /**
     * Mọi từ khoá xuất hiện trong {@code text} như một từ độc lập (đúng ranh giới chữ/số). Văn bản và từ khoá phải cùng dạng chuẩn
     * hoá (ở đây: NFC, chữ thường), vì so khớp theo từng ký tự.
     */
    public Set<String> findWholeWords(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isEmpty() || size == 0) return found;

        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != root && node.child(c) == null) node = node.fail;
            Node next = node.child(c);
            node = next != null ? next : root;

            for (Node out = node.key != null ? node : node.dict; out != null; out = out.dict) {
                String key = out.key;
                if (found.contains(key)) continue;
                int start = i - key.length() + 1;
                if (isBoundaryBefore(text, start) && isBoundaryAfter(text, i + 1)) {
                    found.add(key);
                }
            }
        }
        return found;
    }

    private static boolean isBoundaryBefore(String text, int start) {
        return start <= 0 || !isWordChar(text.codePointBefore(start));
    }

    private static boolean isBoundaryAfter(String text, int end) {
        return end >= text.length() || !isWordChar(text.codePointAt(end));
    }

    private static boolean isWordChar(int codePoint) {
        return Character.isLetter(codePoint) || Character.isDigit(codePoint);
    }
}
