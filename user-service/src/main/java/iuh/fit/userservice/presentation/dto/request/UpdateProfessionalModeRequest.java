package iuh.fit.userservice.presentation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfessionalModeRequest {

    Boolean isProfessionalMode;

    @Size(max = 100, message = "Creator category cannot exceed 100 characters")
    String creatorCategory;
}
