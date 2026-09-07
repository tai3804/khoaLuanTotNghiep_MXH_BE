package iuh.fit.postservice.application.features.reaction.queries.get_post_reactions;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.mapper.ReactionFeatureMapper;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPostReactionsQueryHandler {

    ReactionRepository reactionRepository;
    ReactionFeatureMapper reactionFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostReactionsResult> handle(GetPostReactionsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by("createdAt").descending());
        Page<Reaction> reactionsPage = reactionRepository.findByPostId(query.getPostId(), pageable);

        List<GetPostReactionsResult> content = reactionsPage.getContent().stream()
                .map(reactionFeatureMapper::toGetReactionsResult)
                .toList();

        return reactionFeatureMapper.toPagedResponse(reactionsPage, content);
    }
}
