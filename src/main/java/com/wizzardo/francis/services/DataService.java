package com.wizzardo.francis.services;

import com.wizzardo.http.framework.di.Service;
import com.wizzardo.tools.collections.flow.Flow;
import com.wizzardo.tools.interfaces.Mapper;
import com.wizzardo.tools.misc.Unchecked;

import java.sql.ResultSet;
import java.util.Map;

import static com.wizzardo.francis.services.DBService.args;

/**
 * Created by wizzardo on 04/02/17.
 */
public class DataService implements Service {

    protected static final Mapper<Flow<ResultSet>, Long> FLOW_FIRST_LONG = flow ->
            flow.map(rs -> Unchecked.call(() -> rs.getLong(1))).first().get();

    DBService dbService;

    public boolean isApplicationExists(String appName) {
        return dbService.executeQuery("select id from application where name = ? limit 1", args(appName), flow ->
                flow.count().get() == 1
        );
    }

    public Long findApplicationId(String appName) {
        return dbService.executeQuery("select id from application where name = ? limit 1", args(appName), FLOW_FIRST_LONG);
    }

    public Long saveApplication(String appName) {
        return dbService.executeQuery("insert into application (name) values (?) RETURNING ID", args(appName), FLOW_FIRST_LONG);
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
}
