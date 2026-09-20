package iuh.fit.adminservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostPagedResponse {
    private int code;
    private List<Object> data;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private boolean last;
    private String message;
}
