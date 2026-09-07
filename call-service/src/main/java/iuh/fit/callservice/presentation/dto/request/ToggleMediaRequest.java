package iuh.fit.callservice.presentation.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleMediaRequest {
    Boolean audioMuted;
    Boolean videoMuted;
}
