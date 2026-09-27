package iuh.fit.aiservice.application.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class PostResponse {
    private UUID id;
    private String content;
}
