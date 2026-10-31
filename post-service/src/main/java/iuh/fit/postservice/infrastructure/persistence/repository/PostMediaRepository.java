package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.PostMedia;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostMediaRepository extends BaseJpaRepository<PostMedia, UUID> {
    List<PostMedia> findByPostIdOrderBySortOrderAsc(UUID postId);
    List<PostMedia> findByPostIdInOrderBySortOrderAsc(List<UUID> postIds);

    @Modifying
    @Transactional
    void deleteByPostId(UUID postId);
}
