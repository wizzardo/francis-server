package com.wizzardo.francis.services.orm;

/**
 * Created by wizzardo on 27/03/17.
 */
public interface CrudRepository<T, I> {
    <S extends T> S save(S entity);

    T findOne(I primaryKey);

    Iterable<T> findAll();

    Long count();

    void delete(T entity);

    boolean exists(I primaryKey);
}
