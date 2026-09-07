package iuh.fit.callservice.application.features.call.commands.toggle_media;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToggleMediaCommandHandler {

    CallParticipantRepository callParticipantRepository;

    @Transactional
    public void handle(ToggleMediaCommand command) {
        CallParticipant participant = callParticipantRepository
                .findByCallSessionIdAndUserId(command.getCallSessionId(), command.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.NOT_A_CALL_PARTICIPANT));

        if (command.getAudioMuted() != null) {
            participant.setAudioMuted(command.getAudioMuted());
        }
        if (command.getVideoMuted() != null) {
            participant.setVideoMuted(command.getVideoMuted());
        }

        callParticipantRepository.save(participant);
    }
}
