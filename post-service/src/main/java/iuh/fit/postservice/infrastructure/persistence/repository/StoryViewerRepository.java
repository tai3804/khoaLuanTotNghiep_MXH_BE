package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.StoryViewer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StoryViewerRepository extends BaseJpaRepository<StoryViewer, UUID> {

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    long countByStoryId(UUID storyId);

    Page<StoryViewer> findByStoryIdOrderByViewedAtDesc(UUID storyId, Pageable pageable);

    List<StoryViewer> findByStoryId(UUID storyId);

    /** Đếm lượt xem cho nhiều story trong một truy vấn: mỗi dòng là [storyId, số lượt]. */
    @Query("SELECT v.storyId, COUNT(v) FROM StoryViewer v WHERE v.storyId IN :storyIds GROUP BY v.storyId")
    List<Object[]> countByStoryIds(@Param("storyIds") Collection<UUID> storyIds);

    /** Trong số các story cho trước, những story mà viewer đã xem. */
    @Query("SELECT v.storyId FROM StoryViewer v WHERE v.viewerId = :viewerId AND v.storyId IN :storyIds")
    List<UUID> findViewedStoryIds(@Param("viewerId") UUID viewerId, @Param("storyIds") Collection<UUID> storyIds);
}
