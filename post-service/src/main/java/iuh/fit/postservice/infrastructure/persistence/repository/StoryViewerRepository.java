package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.StoryViewer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoryViewerRepository extends BaseJpaRepository<StoryViewer, UUID> {

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    long countByStoryId(UUID storyId);

    Page<StoryViewer> findByStoryIdOrderByViewedAtDesc(UUID storyId, Pageable pageable);

    List<StoryViewer> findByStoryId(UUID storyId);
}
