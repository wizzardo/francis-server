package com.wizzardo.francis.services;

import com.wizzardo.francis.domain.Transformation;
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

    DBService dbService;

    public boolean isApplicationExists(String appName) {
        return findApplicationId(appName) != null;
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


    public List<Transformation> findAllTransformationsByApplicationId(long applicationId) {
        return dbService.executeQuery("select" +
                " id," +
                " application_id," +
                " version," +
                " last_updated," +
                " date_created," +
                " class_name," +
                " method," +
                " method_descriptor," +
                " before," +
                " after," +
                " variables " +
                " from transformation where application_id = ?", args(applicationId), flow -> flow
                .map(rs -> Unchecked.call(() -> {
                    Transformation t = new Transformation();
                    t.id = rs.getLong(1);
                    t.applicationId = rs.getLong(2);
                    t.version = rs.getLong(3);
                    t.lastUpdated = rs.getDate(4);
                    t.dateCreated = rs.getDate(5);
                    t.className = rs.getString(6);
                    t.method = rs.getString(7);
                    t.methodDescriptor = rs.getString(8);
                    t.before = rs.getString(9);
                    t.after = rs.getString(10);
                    t.variables = rs.getString(11);
                    return t;
                }))
                .toList()
                .get()
        );
    }

    public Transformation saveTransformation(Transformation t) {
        t.id = dbService.executeQuery("insert into transformation (" +
                        " application_id," +
                        " version," +
                        " last_updated," +
                        " date_created," +
                        " class_name," +
                        " method," +
                        " method_descriptor," +
                        " before," +
                        " after," +
                        " variables " +
                        ") values (?,?,now(),now(),?, ?,?,?,?,?) RETURNING ID",
                args(t.applicationId, t.version, t.className,
                        t.method, t.methodDescriptor, t.before, t.after, t.variables), FLOW_FIRST_LONG);
        return t;
    }

    public void updateTransformation(Transformation t) {
        dbService.executeUpdate("update transformation set " +
                        " version = version + 1," +
                        " last_updated = now()," +
                        " class_name = ?," +
                        " method = ?," +
                        " method_descriptor = ?," +
                        " before = ?," +
                        " after = ?," +
                        " variables = ?" +
                        " where" +
                        " id = ?",
                args(t.className, t.method, t.methodDescriptor,
                        t.before, t.after, t.variables, t.id));
    }

    public boolean deleteTransformation(Transformation t) {
        return 1 == dbService.executeUpdate("delete from transformation where id = ?", args(t.id));
    }
}
