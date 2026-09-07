package iuh.fit.mediaservice.infrastructure.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] bytes;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] bytes, String originalFilename, String contentType) {
        this.bytes = bytes != null ? bytes : new byte[0];
        this.originalFilename = originalFilename != null ? originalFilename : "file";
        this.name = this.originalFilename;
        this.contentType = contentType != null ? contentType : "application/octet-stream";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IllegalStateException {
        throw new UnsupportedOperationException("transferTo is not supported");
    }
}
