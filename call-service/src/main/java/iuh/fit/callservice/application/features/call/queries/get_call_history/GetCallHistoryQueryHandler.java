package iuh.fit.callservice.application.features.call.queries.get_call_history;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.callservice.application.mapper.CallFeatureMapper;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetCallHistoryQueryHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetCallHistoryResult> handle(GetCallHistoryQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageZeroBased = Math.max(0, filter.getPage() - 1);
        int size = filter.getSize() > 0 ? filter.getSize() : 10;

        PageRequest pageRequest = PageRequest.of(pageZeroBased, size);
        Page<CallParticipant> participantPage = callParticipantRepository.findUserCallHistory(query.getCurrentUserId(), pageRequest);

        List<GetCallHistoryResult> results = new ArrayList<>();
        for (CallParticipant participant : participantPage.getContent()) {
            Optional<CallSession> sessionOpt = callSessionRepository.findById(participant.getCallSessionId());
            if (sessionOpt.isPresent()) {
                GetCallHistoryResult result = callFeatureMapper.toGetCallHistoryResult(sessionOpt.get(), participant);
                result.setMyRole(participant.getRole());
                result.setMyStatus(participant.getStatus());
                results.add(result);
            }
        }

        return PagedResponse.<GetCallHistoryResult>builder()
                .content(results)
                .page(participantPage.getNumber())
                .size(participantPage.getSize())
                .totalElements(participantPage.getTotalElements())
                .totalPages(participantPage.getTotalPages())
                .last(participantPage.isLast())
                .build();
    }
}
