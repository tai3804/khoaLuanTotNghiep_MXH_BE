package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.PostMedia;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostMediaRepository extends BaseJpaRepository<PostMedia, UUID> {
    List<PostMedia> findByPostIdOrderBySortOrderAsc(UUID postId);
    void deleteByPostId(UUID postId);
}
