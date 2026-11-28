package iuh.fit.commonframework.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Áp dụng "dung lượng tệp tải lên tối đa" ({@code max_upload_size_mb}) cho mọi API nhận tệp (bài viết, ảnh đại diện, tệp trong
 * trò chuyện...): request multipart có một tệp vượt giới hạn bị trả 413 trước khi tới controller. Giới hạn cứng của Spring
 * (spring.servlet.multipart.max-file-size) vẫn là trần trên cùng.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 90)
@RequiredArgsConstructor
public class UploadLimitFilter extends OncePerRequestFilter {

    static final int DEFAULT_MAX_MB = 25;

    private final SystemConfigReader systemConfigReader;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ct = request.getContentType();
        return ct == null || !ct.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int maxMb = systemConfigReader.intValue(SystemConfigReader.MAX_UPLOAD_SIZE_MB, DEFAULT_MAX_MB);
        long maxBytes = Math.max(1, maxMb) * 1024L * 1024L;
        try {
            for (Part part : request.getParts()) {
                if (part.getSubmittedFileName() != null && part.getSize() > maxBytes) {
                    reject(response, part.getSubmittedFileName(), part.getSize(), maxMb);
                    return;
                }
            }
        } catch (IllegalStateException | ServletException e) {
            // Vượt trần cứng của Spring hoặc request không đọc được: để Spring trả lỗi như cũ
            log.debug("Skip upload limit check: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String fileName, long size, int maxMb) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        String safeName = fileName.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write(String.format(
                "{\"code\":413,\"message\":\"Tệp \\\"%s\\\" (%.1f MB) vượt quá dung lượng cho phép %d MB.\",\"maxUploadSizeMb\":%d}",
                safeName, size / 1024.0 / 1024.0, maxMb, maxMb));
    }
}
