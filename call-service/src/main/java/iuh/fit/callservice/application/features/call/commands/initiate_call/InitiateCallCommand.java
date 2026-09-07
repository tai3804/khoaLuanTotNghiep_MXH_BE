package iuh.fit.callservice.application.features.call.commands.initiate_call;

import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.MediaType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitiateCallCommand {
    UUID currentUserId;
    ChannelType channelType;
    MediaType mediaType;
    UUID conversationId;
    Set<UUID> targetUserIds;
}
