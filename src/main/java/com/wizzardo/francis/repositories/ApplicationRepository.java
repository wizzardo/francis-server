package com.wizzardo.francis.repositories;

import com.wizzardo.francis.domain.Application;
import com.wizzardo.francis.services.orm.CrudRepository;

import java.util.List;

/**
 * Created by wizzardo on 27/03/17.
 */
public interface ApplicationRepository extends CrudRepository<Application, Long> {
    Application findByName(String name);

    List<Application> getAll();

    Application get();
}
