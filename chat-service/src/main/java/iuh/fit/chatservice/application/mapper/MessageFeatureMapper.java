package iuh.fit.chatservice.application.mapper;

import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageResult;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageFeatureMapper {

    @Mapping(target = "messageId", source = "id")
    SendMessageResult toSendMessageResult(Message message);

    @Mapping(target = "messageId", source = "id")
    GetMessagesResult toGetMessagesResult(Message message);
}
