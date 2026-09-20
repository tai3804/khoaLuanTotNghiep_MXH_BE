package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateGroupRequest {
    
    @NotBlank(message = "TĂªn nhĂ³m khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    String name;
    
    String description;
    
    @NotNull(message = "Quyá»n riĂªng tÆ° khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    GroupPrivacy privacy;
}

