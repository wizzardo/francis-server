package com.wizzardo.francis.services;

import com.wizzardo.francis.domain.Application;
import com.wizzardo.francis.domain.Transformation;
import com.wizzardo.francis.repositories.ApplicationRepository;
import com.wizzardo.francis.repositories.TransformationRepository;
import com.wizzardo.http.framework.di.Service;
import com.wizzardo.tools.collections.flow.Flow;
import com.wizzardo.tools.interfaces.Mapper;
import com.wizzardo.tools.misc.Unchecked;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static com.wizzardo.francis.services.DBService.args;

/**
 * Created by wizzardo on 04/02/17.
 */
public class DataService implements Service {
    protected static final Mapper<Flow<ResultSet>, Long> FLOW_FIRST_LONG = flow ->
            flow.map(rs -> Unchecked.call(() -> rs.getLong(1))).first().get();

    TransformationRepository transformationRepository;
    ApplicationRepository applicationRepository;

    DBService dbService;

    public boolean isApplicationExists(String appName) {
        return findApplicationId(appName) != null;
    }

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


    public Long findInstanceId(Long applicationId, String mac) {
        return dbService.executeQuery("select id from instance where application_id = ? and mac = ? limit 1", args(applicationId, mac), FLOW_FIRST_LONG);
    }

    public Long saveInstance(Long applicationId, String version, String mac, String ip, String hostname) {
        return dbService.executeQuery("insert into instance (application_id, version, mac, ip, hostname) values (?,?,?,?,?) RETURNING ID",
                args(applicationId, version, mac, ip, hostname), FLOW_FIRST_LONG);
    }

    public Long saveInstanceIfNot(Long applicationId, Map<String, String> params) {
        Long instanceId = findInstanceId(applicationId, params.get("mac"));
        if (instanceId != null)
            return instanceId;

        return saveInstance(applicationId, params.get("version"), params.get("mac"), params.get("ip"), params.get("hostname"));
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
