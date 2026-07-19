package iuh.fit.commonframework.infrastructure.persistence.repository_impl;

import iuh.fit.commonframework.application.mapper.BaseMapper;
import iuh.fit.commonframework.domain.repository.BaseRepository;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public abstract class BaseRepositoryImpl<D, ID extends Serializable, E> implements BaseRepository<D, ID> {

    protected final BaseJpaRepository<E, ID> jpaRepository;
    protected final BaseMapper<E, D> mapper;

    @Override
    public D save(D entity) {
        E dbModel = mapper.toEntity(entity);
        E savedModel = jpaRepository.save(dbModel);
        return mapper.toDto(savedModel);
    }

    @Override
    public List<D> saveAll(List<D> entities) {
        List<E> dbModels = mapper.toEntity(entities);
        List<E> savedModels = jpaRepository.saveAll(dbModels);
        return mapper.toDto(savedModels);
    }

    @Override
    public Optional<D> findById(ID id) {
        return jpaRepository.findById(id).map(mapper::toDto);
    }

    @Override
    public boolean existsById(ID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public List<D> findAll() {
        return mapper.toDto(jpaRepository.findAll());
    }

    @Override
    public List<D> findAllById(List<ID> ids) {
        return mapper.toDto(jpaRepository.findAllById(ids));
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public void deleteById(ID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void delete(D entity) {
        jpaRepository.delete(mapper.toEntity(entity));
    }

    @Override
    public void deleteAllById(List<ID> ids) {
        jpaRepository.deleteAllById(ids);
    }

    @Override
    public void deleteAll(List<D> entities) {
        jpaRepository.deleteAll(mapper.toEntity(entities));
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
