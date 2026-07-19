package iuh.fit.commonframework.domain.repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public interface BaseRepository<D, ID extends Serializable> {
    D save(D entity);
    List<D> saveAll(List<D> entities);
    Optional<D> findById(ID id);
    boolean existsById(ID id);
    List<D> findAll();
    List<D> findAllById(List<ID> ids);
    long count();
    void deleteById(ID id);
    void delete(D entity);
    void deleteAllById(List<ID> ids);
    void deleteAll(List<D> entities);
    void deleteAll();
}
