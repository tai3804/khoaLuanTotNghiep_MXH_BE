package iuh.fit.adminservice.application.features.report.commands.create_ai_flag;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateAiFlagReportCommand {
    UUID postId;
    UUID authorId;
    double score;
    String category;
    String severity;
    String reason;
}
