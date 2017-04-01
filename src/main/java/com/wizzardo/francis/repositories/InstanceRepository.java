package com.wizzardo.francis.repositories;

import com.wizzardo.francis.domain.Instance;
import com.wizzardo.francis.services.orm.CrudRepository;

/**
 * Created by wizzardo on 27/03/17.
 */
public interface InstanceRepository extends CrudRepository<Instance, Long> {

    Instance findByApplicationIdAndMac(Long applicationId, String mac);
}
