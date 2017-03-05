package com.wizzardo.francis.services;

import com.wizzardo.francis.domain.Application;
import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.framework.di.Service;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.collections.flow.Flow;
import com.wizzardo.tools.collections.flow.FlowProcessor;
import com.wizzardo.tools.interfaces.Mapper;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.misc.pool.Pool;
import com.wizzardo.tools.misc.pool.PoolBuilder;
import com.wizzardo.tools.misc.pool.SimpleHolder;
import com.wizzardo.tools.reflection.FieldInfo;
import com.wizzardo.tools.reflection.FieldReflection;
import com.wizzardo.tools.reflection.Fields;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

/**
 * Created by wizzardo on 24/01/17.
 */
public class DBService implements Service, PostConstruct {
    protected static final char[] SQL_CHARS_TABLE = new char[128];

    Cache<Class, PreparedSelect> preparedSelects = new Cache<>("preparedSelects", 0);

    Pool<Connection> connectionPool = new PoolBuilder<Connection>()
            .holder(SimpleHolder::new)
            .supplier(this::createConnection)
            .queue(PoolBuilder.createSharedQueueSupplier())
            .limitSize(16)
            .build();

    static {
        String s = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            SQL_CHARS_TABLE[c] = ("" + c).toLowerCase().charAt(0);
        }
    }

    @Override
    public void init() {
        Unchecked.call(() -> Class.forName("org.postgresql.Driver"));
//        Unchecked.call(() -> Class.forName("com.mysql.jdbc.Driver"));
    }

    public <R> R provide(Pool.UnsafeMapper<Connection, R> mapper) {
        return connectionPool.provide(mapper);
    }

    private Connection createConnection() {
        return Unchecked.call(() -> {
            String url = "jdbc:postgresql://localhost/francis";
//            String url = "jdbc:mysql://10.0.3.124:3306/test";
            return DriverManager.getConnection(url, "username", "password");
        });
    }

    static class FlowSql extends Flow<ResultSet> {

        boolean stop = false;

        public static Flow<ResultSet> of(ResultSet rs) {
            return new FlowSql(rs);
        }

        @Override
        protected void start() {
            if (child == null)
                return;

            process();

            onEnd();
        }

        final ResultSet resultSet;

        public FlowSql(ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        protected void process() {
            FlowProcessor<ResultSet, ?> child = this.child;

            try (ResultSet rs = this.resultSet;) {
                while (rs.next()) {
                    if (stop)
                        break;

                    child.process(rs);
                }
            } catch (SQLException e) {
                throw Unchecked.rethrow(e);
            }
        }
    }

    public <R> R executeQuery(String sql, Mapper<Flow<ResultSet>, R> mapper) {
//        System.out.println("executeQuery: " + sql);
        return provide(connection -> mapper.map(FlowSql.of(connection.createStatement().executeQuery(sql))));
    }

    public <R> R executeQuery(String sql, Object[] args, Mapper<Flow<ResultSet>, R> mapper) {
//        System.out.println("executeQuery: " + sql + " with args: " + Arrays.toString(args));
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return mapper.map(FlowSql.of(statement.executeQuery()));
        });
    }

    public int executeUpdate(String sql, Object[] args) {
//        System.out.println("executeUpdate: " + sql + " with args: " + Arrays.toString(args));
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement.executeUpdate();
        });
    }

    public <R> R execute(String sql, Object[] args, Mapper<Flow<ResultSet>, R> mapper) {
        System.out.println("execute: " + sql + " with args: " + Arrays.toString(args));
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            boolean execute = statement.execute();
            if (execute)
                return mapper.map(FlowSql.of(statement.getResultSet()));
            else
                throw new RuntimeException("Expected ResultSet");
        });
    }

    public void execute(String sql) {
        provide(connection -> connection.createStatement().execute(sql));
    }

    public static Object[] args(Object... args) {
        return args;
    }

    interface Binder<T> {
        void bind(T t, ResultSet rs) throws SQLException;
    }

    public <T> T get(Long id, Class<T> clazz) {
        PreparedSelect<T> select = prepareSelect(clazz);
        StringBuilder sb = new StringBuilder(select.query.length() + 20);
        sb.append(select.query).append(" where id = ").append(id);
        T result = executeQuery(sb.toString(), flow -> flow
                .map(select.mapper)
                .first()
                .get()
        );
        return result;
    }

    public <T> Mapper<Object[], T> createGetBy(String field, Class<T> clazz) {
        PreparedSelect<T> select = prepareSelect(clazz);
        String sql = select.query + " where " + field + "=?";
        return args -> executeQuery(sql, args, flow -> flow
                .map(select.mapper)
                .first()
                .get()
        );
    }

    public <T> List<T> list(Class<T> clazz) {
        PreparedSelect<T> select = prepareSelect(clazz);
        List<T> result = executeQuery(select.query, flow -> flow
                .map(select.mapper)
                .toList()
                .get()
        );
        return result;
    }

    protected <T> PreparedSelect<T> prepareSelect(Class<T> clazz) {
        PreparedSelect<T> preparedSelect = preparedSelects.get(clazz);
        if (preparedSelect != null)
            return preparedSelect;

        preparedSelects.put(clazz, preparedSelect = createPreparedSelect(clazz));
        return preparedSelect;
    }

    protected <T> PreparedSelect<T> createPreparedSelect(Class<T> clazz) {
        PreparedSelect<T> preparedSelect;
        StringBuilder sb = new StringBuilder(64);
        Fields<FieldInfo> fields = new Fields<>(clazz);
        sb.append("select ");
        boolean comma = false;
        int counter = 0;
        Binder<T>[] binders = new Binder[fields.size()];
        for (FieldInfo field : fields) {
            if (comma)
                sb.append(',');
            else
                comma = true;

            sb.append(toSqlString(field.field.getName()));
            binders[counter] = getBinder(clazz, field, counter + 1);
            counter++;
        }
        sb.append(" from ").append(toSqlString(clazz.getSimpleName()));
        preparedSelect = new PreparedSelect<>(sb.toString(), rs -> Unchecked.call(() -> {
            T t = clazz.newInstance();
            for (Binder<T> binder : binders) {
                binder.bind(t, rs);
            }
            return t;
        }));
        return preparedSelect;
    }

    public <T> Binder<T> getBinder(Class<T> clazz, FieldInfo field, int i) {
        FieldReflection reflection = field.reflection;
        switch (reflection.getType()) {
            case BOOLEAN:
                return (t, rs) -> reflection.setBoolean(t, rs.getBoolean(i));
            case BYTE:
                return (t, rs) -> reflection.setByte(t, rs.getByte(i));
            case SHORT:
                return (t, rs) -> reflection.setShort(t, rs.getShort(i));
            case INTEGER:
                return (t, rs) -> reflection.setInteger(t, rs.getInt(i));
            case LONG:
                return (t, rs) -> reflection.setLong(t, rs.getLong(i));
            case FLOAT:
                return (t, rs) -> reflection.setFloat(t, rs.getFloat(i));
            case DOUBLE:
                return (t, rs) -> reflection.setDouble(t, rs.getDouble(i));
            case CHAR:
                return (t, rs) -> reflection.setChar(t, rs.getString(i).charAt(0));
            case OBJECT:
                if (field.generic.clazz.equals(String.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getString(i));
                } else if (field.generic.clazz.equals(Date.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getDate(i));
                } else if (field.generic.clazz.equals(Boolean.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getBoolean(i));
                } else if (field.generic.clazz.equals(Byte.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getByte(i));
                } else if (field.generic.clazz.equals(Short.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getShort(i));
                } else if (field.generic.clazz.equals(Integer.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getInt(i));
                } else if (field.generic.clazz.equals(Long.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getLong(i));
                } else if (field.generic.clazz.equals(Float.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getFloat(i));
                } else if (field.generic.clazz.equals(Double.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getDouble(i));
                } else if (field.generic.clazz.equals(Character.class)) {
                    return (t, rs) -> reflection.setObject(t, rs.getString(i).charAt(0));
                }

            default:
                throw new IllegalArgumentException("Cannot map field of class '" + clazz + "' with type '" + field.generic.clazz + "'");
        }
    }

    protected String toSqlString(String name) {
        int length = name.length();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            char lowerCase = SQL_CHARS_TABLE[c];
            if (c != lowerCase && i != 0) {
                sb.append("_");
            }
            sb.append(lowerCase);
        }
        return sb.toString();
    }

    public static class PreparedSelect<T> {
        public final String query;
        public final Mapper<ResultSet, T> mapper;

        public PreparedSelect(String query, Mapper<ResultSet, T> mapper) {
            this.query = query;
            this.mapper = mapper;
        }
    }

    interface ApplicationRepository {
        Application findByName(String name);
    }

    public static void main(String[] main) throws IOException, InterruptedException {
        DBService dbService = new DBService();
        dbService.init();

        DataService dataService = new DataService();
        dataService.dbService = dbService;

        Application t = dbService.get(1l, Application.class);
        System.out.println(t);
        System.out.println(dbService.list(Application.class));

//        String scheme = new String(IOTools.bytes(DBService.class.getResourceAsStream("/Schema.sql")));
//        System.out.println("applying scheme:");
//        for (String s : scheme.split(";")) {
//            System.out.println(s);
//            dbService.execute(s);
//        }

        ApplicationRepository proxy = dbService.createRepositoryInstance(ApplicationRepository.class);
        System.out.println(proxy.findByName("idea"));
    }

    protected <T> T createRepositoryInstance(Class<T> repositoryClass) {
        Object o = Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                new InvocationHandler() {
                    Cache<Method, Mapper<Object[], Object>> mapperCache = new Cache<>(repositoryClass.getSimpleName() + "Mappers", 0);

                    @Override
                    public Object invoke(Object instance, Method method, Object[] args) throws Throwable {
                        return getMapper(method, args).map(args);
                    }

                    Mapper<Object[], ?> getMapper(Method method, Object[] args) {
                        Mapper<Object[], Object> mapper = mapperCache.get(method);
                        if (mapper != null)
                            return mapper;

                        mapperCache.put(method, mapper = createMapper(method, args));
                        return mapper;
                    }

                });

        return (T) o;
    }

    protected <T> Mapper<Object[], T> createMapper(Method method, Object[] args) {
        //TODO: http://docs.spring.io/spring-data/jpa/docs/1.4.3.RELEASE/reference/html/repositories.html#repositories.query-methods.query-creation
        //TODO: http://docs.spring.io/spring-data/jpa/docs/1.4.3.RELEASE/reference/html/jpa.repositories.html#jpa.query-methods.query-creation
        String name = method.getName();
        Class<T> returnType = (Class<T>) method.getReturnType();
        System.out.println(name + "(" + Arrays.toString(method.getParameterTypes()) + ") invoked with " + Arrays.toString(args));
        if (name.startsWith("findBy")) {
            String field = name.substring("findBy".length());
            if (args.length == 1) {
                return createGetBy(toSqlString(field), returnType);
            }
        }

        throw new IllegalArgumentException("Cannot create mapper for " + name + "(" + Arrays.toString(method.getParameterTypes()) + ")");
    }
}
