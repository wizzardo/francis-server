package com.wizzardo.francis.services;

import com.wizzardo.francis.domain.Application;
import com.wizzardo.francis.domain.Instance;
import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.repositories.ApplicationRepository;
import com.wizzardo.francis.repositories.InstanceRepository;
import com.wizzardo.francis.repositories.TransformationRepository;
import com.wizzardo.http.framework.di.Service;

import java.util.List;
import java.util.Map;

/**
 * Created by wizzardo on 04/02/17.
 */
public class DataService implements Service {
    TransformationRepository transformationRepository;
    ApplicationRepository applicationRepository;
    InstanceRepository instanceRepository;

    public Long findApplicationId(String appName) {
        Application app = applicationRepository.findByName(appName);
        if (app != null)
            return app.id;
        else
            return null;
    }

    public Long saveApplication(String appName) {
        Application app = new Application();
        app.name = appName;
        applicationRepository.save(app);
        return app.id;
    }

    public Long saveApplicationIfNot(String name) {
        Long id = findApplicationId(name);
        if (id != null)
            return id;

        return saveApplication(name);
    }


    public Instance findInstance(Long applicationId, String mac) {
        return instanceRepository.findByApplicationIdAndMac(applicationId, mac);
    }

    public Instance saveInstanceIfNot(Long applicationId, Map<String, String> params) {
        Instance instance = findInstance(applicationId, params.get("mac"));
        if (instance != null)
            return instance;

        instance = new Instance();
        instance.applicationId = applicationId;
        instance.version = params.get("version");
        instance.mac = params.get("mac");
        instance.ip = params.get("ip");
        instance.hostname = params.get("hostname");
        return instanceRepository.save(instance);
    }


    public List<Transformation> findAllTransformationsByApplicationId(long applicationId) {
        return transformationRepository.findAllByApplicationId(applicationId);
    }

    public Transformation saveTransformation(Transformation t) {
        return transformationRepository.save(t);
    }

    public void updateTransformation(Transformation t) {
        transformationRepository.save(t);
    }

    public boolean deleteTransformation(Transformation t) {
        return deleteTransformation(t.id);
    }

    public boolean deleteTransformation(Long id) {
        return transformationRepository.delete(id) == 1;
    }

    public Transformation getTransformation(Long id) {
        return transformationRepository.findOne(id);
    }
}
