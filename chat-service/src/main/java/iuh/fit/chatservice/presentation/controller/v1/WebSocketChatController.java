package iuh.fit.chatservice.presentation.controller.v1;

import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageCommandHandler;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageResult;
import iuh.fit.chatservice.presentation.dto.request.SendMessageRequest;
import iuh.fit.chatservice.presentation.dto.response.MessageResponse;
import iuh.fit.chatservice.presentation.mapper.ChatPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketChatController {

    SendMessageCommandHandler sendMessageCommandHandler;
    SimpMessagingTemplate messagingTemplate;
    ChatPresentationMapper chatPresentationMapper;

    @MessageMapping("/chat.sendMessage/{conversationId}")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload SendMessageRequest request,
            Principal principal) {
        if (principal == null) {
            log.error("Unauthenticated WebSocket message attempt to conversation: {}", conversationId);
            return;
        }

        UUID senderId = null;
        if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
            if (token.getPrincipal() instanceof Jwt jwt) {
                senderId = UUID.fromString(jwt.getSubject());
            }
        }

        if (senderId == null) {
            try {
                senderId = UUID.fromString(principal.getName());
            } catch (Exception e) {
                log.error("Failed to parse senderId from Principal: {}", principal.getName());
                return;
            }
        }

        SendMessageCommand command = chatPresentationMapper.toSendMessageCommand(request, conversationId, senderId);
        SendMessageResult result = sendMessageCommandHandler.handle(command);
        MessageResponse response = chatPresentationMapper.toResponse(result);

        // Broadcast to topic /topic/conversations/{conversationId}
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
    }
}
