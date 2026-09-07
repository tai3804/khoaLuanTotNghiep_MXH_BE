package iuh.fit.callservice.presentation.dto.request;

import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.MediaType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitiateCallRequest {

    @NotNull(message = "{callSession.channelType.required}")
    ChannelType channelType;

    @NotNull(message = "{callSession.mediaType.required}")
    MediaType mediaType;

    UUID conversationId;
    Set<UUID> targetUserIds;
}
