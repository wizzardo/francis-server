package com.wizzardo.francis.repositories;

import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.services.orm.CrudRepository;

import java.util.List;

/**
 * Created by wizzardo on 27/03/17.
 */
public interface TransformationRepository extends CrudRepository<Transformation, Long> {

    Integer delete(Long id);

    List<Transformation> findAllByApplicationId(Long applicationId);
}
