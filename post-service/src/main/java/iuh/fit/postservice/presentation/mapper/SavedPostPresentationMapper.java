package iuh.fit.postservice.presentation.mapper;

import iuh.fit.postservice.application.features.saved_post.commands.save_post.SavePostCommand;
import iuh.fit.postservice.domain.entities.SavedPost;
import iuh.fit.postservice.presentation.dto.request.SavePostRequest;
import iuh.fit.postservice.presentation.dto.response.SavedPostResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SavedPostPresentationMapper {

    @Mapping(target = "userId", source = "userId")
    SavePostCommand toCommand(SavePostRequest request, UUID userId);

    SavedPostResponse toResponse(SavedPost entity);
}
