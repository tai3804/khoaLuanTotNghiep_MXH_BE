package iuh.fit.notificationservice.presentation.mapper;

import iuh.fit.notificationservice.application.features.notification.commands.send_email.SendEmailCommand;
import iuh.fit.notificationservice.presentation.dto.request.SendEmailRequest;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationPresentationMapper {
    SendEmailCommand toCommand(SendEmailRequest request);
}
