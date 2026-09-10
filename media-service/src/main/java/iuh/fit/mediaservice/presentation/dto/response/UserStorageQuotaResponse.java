package iuh.fit.mediaservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStorageQuotaResponse {
    UUID userId;
    long usedBytes;
    long maxQuotaBytes;
    double usedPercentage;
    String usedReadable;
    String maxQuotaReadable;
}
