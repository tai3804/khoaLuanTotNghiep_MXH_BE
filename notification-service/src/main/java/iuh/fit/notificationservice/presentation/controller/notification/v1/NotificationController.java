package iuh.fit.notificationservice.presentation.controller.notification.v1;

import iuh.fit.notificationservice.application.features.notification.commands.send_email.SendEmailCommandHandler;
import iuh.fit.notificationservice.presentation.constants.ApiConstants;
import iuh.fit.notificationservice.presentation.dto.request.SendEmailRequest;
import iuh.fit.notificationservice.presentation.mapper.NotificationPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.NOTIFICATION_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {

    SendEmailCommandHandler sendEmailCommandHandler;
    NotificationPresentationMapper mapper;

    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        sendEmailCommandHandler.handle(mapper.toCommand(request));
        return ResponseEntity.ok("Email sent successfully");
    }
}
