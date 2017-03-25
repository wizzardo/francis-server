package com.wizzardo.francis.services;

import com.wizzardo.francis.domain.Application;
import com.wizzardo.http.framework.di.PostConstruct;
import com.wizzardo.http.framework.di.Service;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.collections.Pair;
import com.wizzardo.tools.collections.flow.Flow;
import com.wizzardo.tools.collections.flow.FlowProcessor;
import com.wizzardo.tools.interfaces.BiConsumer;
import com.wizzardo.tools.interfaces.Mapper;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.misc.pool.Pool;
import com.wizzardo.tools.misc.pool.PoolBuilder;
import com.wizzardo.tools.misc.pool.SimpleHolder;
import com.wizzardo.tools.reflection.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wizzardo on 24/01/17.
 */
public class DBService implements Service, PostConstruct {
    protected static final char[] SQL_CHARS_TABLE = new char[128];

    Cache<Class, PreparedReadQuery> preparedSelects = new Cache<>("preparedSelects", 0);

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
        System.out.println("executeQuery: " + sql + " with args: " + Arrays.toString(args));
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    statement.setObject(i + 1, args[i]);
                }
            }
            return mapper.map(FlowSql.of(statement.executeQuery()));
        });
    }

    public <T, R> R executeQuery(String sql, SqlSetter<T> setter, T t, Mapper<Flow<ResultSet>, R> mapper) {
        System.out.println("executeQuery: " + sql);
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            setter.bind(t, statement);
            return mapper.map(FlowSql.of(statement.executeQuery()));
        });
    }

    public <T> Long executeQuery(String sql, SqlSetter<T> setter, T t) {
//        System.out.println("executeQuery: " + sql + " with args: " + Arrays.toString(args));
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            setter.bind(t, statement);
            return FlowSql.of(statement.executeQuery())
                    .map(rs -> Unchecked.call(() -> rs.getLong(1)))
                    .first()
                    .get();
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

    public <T> int executeUpdate(String sql, SqlSetter<T> setter, T t) {
        System.out.println("executeQuery: " + sql);
        return provide(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            setter.bind(t, statement);
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

    interface SqlGetter<T> {
        void bind(T t, ResultSet rs) throws SQLException;
    }

    interface SqlSetter<T> {
        void bind(T t, PreparedStatement s) throws SQLException;
    }

    public <T> T get(Long id, Class<T> clazz) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        StringBuilder sb = new StringBuilder(select.query.length() + 20);
        sb.append(select.query).append(" where id = ").append(id);
        T result = executeQuery(sb.toString(), flow -> flow
                .map(select.mapper)
                .first()
                .get()
        );
        return result;
    }

    public <T> Mapper<Object[], T> createGetBy(Class<T> clazz, String field) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        String sql = select.query + " where " + field + "=?";
        return args -> executeQuery(sql, args, flow -> flow
                .map(select.mapper)
                .first()
                .get()
        );
    }

    public <T> Mapper<Object[], List<T>> createGetAllBy(Class<T> clazz, String field) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        String sql = select.query + " where " + field + "=?";
        return args -> list(sql, args, select.mapper);
    }

    public <T> Mapper<Object[], T> createGet(Class<T> clazz) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        String sql = select.query + " limit 1";
        return args -> executeQuery(sql, args, flow -> flow
                .map(select.mapper)
                .first()
                .get()
        );
    }

    public <T> Mapper<Object[], List<T>> createGetAll(Class<T> clazz) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        return args -> list(select.query, args, select.mapper);
    }

    public <T> List<T> list(Class<T> clazz) {
        PreparedReadQuery<T> select = prepareSelect(clazz);
        return list(select.query, select.mapper);
    }

    protected <T> List<T> list(String query, Object[] args, Mapper<ResultSet, T> mapper) {
        List<T> result = executeQuery(query, args, flow -> flow
                .map(mapper)
                .toList()
                .get()
        );
        return result;
    }

    protected <T> List<T> list(String query, Mapper<ResultSet, T> mapper) {
        List<T> result = executeQuery(query, flow -> flow
                .map(mapper)
                .toList()
                .get()
        );
        return result;
    }

    protected <T> PreparedReadQuery<T> prepareSelect(Class<T> clazz) {
        PreparedReadQuery<T> preparedReadQuery = preparedSelects.get(clazz);
        if (preparedReadQuery != null)
            return preparedReadQuery;

        preparedSelects.put(clazz, preparedReadQuery = createPreparedSelect(clazz));
        return preparedReadQuery;
    }

    protected <T> PreparedReadQuery<T> createPreparedSelect(Class<T> clazz) {
        PreparedReadQuery<T> preparedReadQuery;
        StringBuilder sb = new StringBuilder(64);
        Fields<FieldInfo> fields = new Fields<>(clazz);
        sb.append("select ");
        boolean comma = false;
        int counter = 0;
        SqlGetter<T>[] getters = new SqlGetter[fields.size()];
        for (FieldInfo field : fields) {
            if (comma)
                sb.append(',');
            else
                comma = true;

            sb.append(toSqlString(field.field.getName()));
            getters[counter] = getGetter(clazz, field, counter + 1);
            counter++;
        }
        sb.append(" from ").append(toSqlString(clazz.getSimpleName()));
        preparedReadQuery = new PreparedReadQuery<>(sb.toString(), rs -> Unchecked.call(() -> {
            T t = clazz.newInstance();
            for (SqlGetter<T> getter : getters) {
                getter.bind(t, rs);
            }
            return t;
        }));
        return preparedReadQuery;
    }

    protected <T> PreparedWriteQuery<T> createPreparedInsert(Class<T> clazz) {
        PreparedWriteQuery<T> preparedWriteQuery;
        StringBuilder sb = new StringBuilder(64);
        Fields<FieldInfo> fields = new Fields<>(clazz);
        sb.append("insert into ").append(toSqlString(clazz.getSimpleName())).append('(');

        StringBuilder fieldsBuilder = new StringBuilder();

        boolean comma = false;
        int counter = 1;
        List<SqlSetter<T>> l = new ArrayList<>();
        for (FieldInfo field : fields) {
            String fieldName = toSqlString(field.field.getName());
            if (fieldName.equals("id"))
                continue;

            if (comma) {
                sb.append(',');
                fieldsBuilder.append(',');
            } else
                comma = true;

            sb.append(fieldName);
            if (Date.class.isAssignableFrom(field.generic.clazz) && (fieldName.contains("create") || fieldName.contains("update"))) {
                fieldsBuilder.append("now()");
            } else {
                fieldsBuilder.append("?");
                l.add(getSetter(clazz, field, counter++));
            }
        }

        sb.append(") values (").append(fieldsBuilder).append(")  RETURNING ID");
        SqlSetter<T>[] setters = l.toArray(new SqlSetter[l.size()]);

        preparedWriteQuery = new PreparedWriteQuery<>(sb.toString(),
                (t, s) -> {
                    for (SqlSetter<T> setter : setters) {
                        setter.bind(t, s);
                    }
                });
        return preparedWriteQuery;
    }

    protected <T> PreparedWriteQuery<T> createPreparedUpdate(Class<T> clazz) {
        PreparedWriteQuery<T> preparedWriteQuery;
        StringBuilder sb = new StringBuilder(64);
        Fields<FieldInfo> fields = new Fields<>(clazz);
        sb.append("update ").append(toSqlString(clazz.getSimpleName())).append(" set ");

        boolean comma = false;
        int counter = 1;
        List<SqlSetter<T>> l = new ArrayList<>();
        for (FieldInfo field : fields) {
            String fieldName = toSqlString(field.field.getName());
            if (fieldName.equals("id"))
                continue;

            if (comma) {
                sb.append(',');
            } else
                comma = true;

            sb.append(fieldName).append('=');
            if (Date.class.isAssignableFrom(field.generic.clazz) && (fieldName.contains("update"))) {
                sb.append("now()");
            } else if (fieldName.equals("version") && (
                    field.generic.clazz == int.class ||
                            field.generic.clazz == long.class ||
                            field.generic.clazz == Integer.class ||
                            field.generic.clazz == Long.class
            )) {
                sb.append("version+1");
            } else {
                sb.append("?");
                l.add(getSetter(clazz, field, counter++));
            }
        }
        sb.append(" where id=?");
        l.add(getSetter(clazz, fields.get("id"), counter++));

        SqlSetter<T>[] setters = l.toArray(new SqlSetter[l.size()]);

        preparedWriteQuery = new PreparedWriteQuery<>(sb.toString(),
                (t, s) -> {
                    for (SqlSetter<T> setter : setters) {
                        setter.bind(t, s);
                    }
                });
        return preparedWriteQuery;
    }

    protected <T> PreparedWriteQuery<T> createPreparedDelete(Class<T> clazz) {
        PreparedWriteQuery<T> preparedWriteQuery;
        StringBuilder sb = new StringBuilder(64);
        Fields<FieldInfo> fields = new Fields<>(clazz);
        sb.append("delete from ")
                .append(toSqlString(clazz.getSimpleName()))
                .append(" where id=?");

        SqlSetter<T> setter = getSetter(clazz, fields.get("id"), 1);
        preparedWriteQuery = new PreparedWriteQuery<>(sb.toString(), setter);
        return preparedWriteQuery;
    }

    public <T> Mapper<? super ResultSet, T> getMapper(Generic returnType) {
        return getMapper(returnType, true);
    }

    public <T> Mapper<? super ResultSet, T> getMapper(Generic returnType, boolean withCollections) {
        Class clazz = returnType.clazz;
        if (clazz == int.class || clazz == Integer.class)
            return rs -> Unchecked.call(() -> (T) (Integer) rs.getInt(1));
        if (clazz == long.class || clazz == Long.class)
            return rs -> Unchecked.call(() -> (T) (Long) rs.getLong(1));
        if (clazz == short.class || clazz == Short.class)
            return rs -> Unchecked.call(() -> (T) (Short) rs.getShort(1));
        if (clazz == byte.class || clazz == Byte.class)
            return rs -> Unchecked.call(() -> (T) (Byte) rs.getByte(1));
        if (clazz == float.class || clazz == Float.class)
            return rs -> Unchecked.call(() -> (T) (Float) rs.getFloat(1));
        if (clazz == double.class || clazz == Double.class)
            return rs -> Unchecked.call(() -> (T) (Double) rs.getDouble(1));
        if (clazz == boolean.class || clazz == Boolean.class)
            return rs -> Unchecked.call(() -> (T) (Boolean) rs.getBoolean(1));
        if (clazz == char.class || clazz == Character.class)
            return rs -> Unchecked.call(() -> (T) (Character) rs.getString(1).charAt(0));
        if (clazz == String.class)
            return rs -> Unchecked.call(() -> (T) rs.getString(1));
        if (clazz == Date.class)
            return rs -> Unchecked.call(() -> (T) rs.getDate(1));
        if (Iterable.class.isAssignableFrom(clazz))
            if (withCollections)
                return getMapper(returnType.type(0), false);
            else
                throw new IllegalArgumentException("Nested iterables are not supported");


        PreparedReadQuery<T> preparedReadQuery = prepareSelect(clazz);
        return preparedReadQuery.mapper;
    }

    protected <T> Mapper<Flow, T> chooseResult(Generic returnType) {
        Class clazz = returnType.clazz;
        if (Iterable.class.isAssignableFrom(clazz))
            return (flow) -> (T) flow.toList().get();
        else
            return flow -> (T) flow.first().get();
    }

    public <T> SqlGetter<T> getGetter(Class<T> clazz, FieldInfo field, int i) {
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

    public <T> SqlSetter<T> getSetter(Class<T> clazz, FieldInfo field, int i) {
        FieldReflection reflection = field.reflection;
        switch (reflection.getType()) {
            case BOOLEAN:
                return (t, s) -> s.setBoolean(i, reflection.getBoolean(t));
            case BYTE:
                return (t, s) -> s.setByte(i, reflection.getByte(t));
            case SHORT:
                return (t, s) -> s.setShort(i, reflection.getShort(t));
            case INTEGER:
                return (t, s) -> s.setInt(i, reflection.getInteger(t));
            case LONG:
                return (t, s) -> s.setLong(i, reflection.getLong(t));
            case FLOAT:
                return (t, s) -> s.setFloat(i, reflection.getFloat(t));
            case DOUBLE:
                return (t, s) -> s.setDouble(i, reflection.getDouble(t));
            case CHAR:
                return (t, s) -> s.setString(i, String.valueOf(reflection.getChar(t)));
            case OBJECT:
                if (field.generic.clazz.equals(String.class)) {
                    return (t, s) -> s.setString(i, (String) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Date.class)) {
                    return (t, s) -> s.setDate(i, new java.sql.Date(((Date) reflection.getObject(t)).getTime()));
                } else if (field.generic.clazz.equals(java.sql.Date.class)) {
                    return (t, s) -> s.setDate(i, (java.sql.Date) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Boolean.class)) {
                    return (t, s) -> s.setBoolean(i, (Boolean) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Byte.class)) {
                    return (t, s) -> s.setByte(i, (Byte) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Short.class)) {
                    return (t, s) -> s.setShort(i, (Short) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Integer.class)) {
                    return (t, s) -> s.setInt(i, (Integer) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Long.class)) {
                    return (t, s) -> s.setLong(i, (Long) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Float.class)) {
                    return (t, s) -> s.setFloat(i, (Float) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Double.class)) {
                    return (t, s) -> s.setDouble(i, (Double) reflection.getObject(t));
                } else if (field.generic.clazz.equals(Character.class)) {
                    return (t, s) -> s.setString(i, String.valueOf((Character) reflection.getObject(t)));
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

    public static class PreparedReadQuery<T> {
        public final String query;
        public final Mapper<ResultSet, T> mapper;

        public PreparedReadQuery(String query, Mapper<ResultSet, T> mapper) {
            this.query = query;
            this.mapper = mapper;
        }
    }

    public static class PreparedWriteQuery<T> {
        public final String query;
        public final SqlSetter<T> setter;

        public PreparedWriteQuery(String query, SqlSetter<T> setter) {
            this.query = query;
            this.setter = setter;
        }
    }

    interface CrudRepository<T, I> {
        <S extends T> S save(S entity);

        T findOne(I primaryKey);

        Iterable<T> findAll();

        Long count();

        void delete(T entity);

        boolean exists(I primaryKey);
    }

    interface ApplicationRepository extends CrudRepository<Application, Long> {
        Application getByName(String name);

        Application findByName(String name);

        List<Application> getAll();

        Application get();
    }

    static void test(String a, String b) {
        if (!a.equals(b))
            throw new AssertionError(a + " != " + b);
    }

    public static void main(String[] main) throws IOException, InterruptedException {
        DBService dbService = new DBService();
        dbService.init();

        test(" where name=? and age=?", dbService.prepareArguments("NameAndAge").toString());
        test(" where name=? or age=?", dbService.prepareArguments("NameOrAge").toString());
        test(" where name=? or age=?", dbService.prepareArguments("NameIsOrAgeIs").toString());
        test(" where age between ? and ?", dbService.prepareArguments("AgeBetween").toString());
        test(" where age<?", dbService.prepareArguments("AgeLessThan").toString());
        test(" where age>?", dbService.prepareArguments("AgeGreaterThan").toString());
        test(" where age<?", dbService.prepareArguments("AgeBefore").toString());
        test(" where age>?", dbService.prepareArguments("AgeAfter").toString());
        test(" where age is null", dbService.prepareArguments("AgeIsNull").toString());
        test(" where age not null", dbService.prepareArguments("AgeIsNotNull").toString());
        test(" where age not null", dbService.prepareArguments("AgeNotNull").toString());
        test(" where age like ?", dbService.prepareArguments("AgeLike").toString());
        test(" where age<>?", dbService.prepareArguments("AgeNot").toString());
        test(" where age in ?", dbService.prepareArguments("AgeIn").toString());
        test(" where age not in ?", dbService.prepareArguments("AgeNotIn").toString());
        test(" where active=true", dbService.prepareArguments("ActiveTrue").toString());
        test(" where active=false", dbService.prepareArguments("ActiveFalse").toString());

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

//        PreparedWriteQuery<Application> preparedWriteQuery = dbService.createPreparedInsert(Application.class);
//        t = new Application();
//        t.name = "test";
//        System.out.println(dbService.executeQuery(preparedWriteQuery.query, preparedWriteQuery.setter, t));


        ApplicationRepository proxy = dbService.createRepositoryInstance(ApplicationRepository.class);
        System.out.println(proxy.getByName("idea"));
        System.out.println(proxy.findByName("idea"));
        System.out.println(proxy.getAll());
        System.out.println(proxy.get());
        System.out.println(proxy.count());

        Application test = proxy.findByName("test");
        if (test != null) {
            System.out.println(proxy.exists(test.id));
            proxy.delete(test);
            System.out.println(proxy.exists(test.id));
        } else {
            test = new Application();
            test.name = "test";
            Application saved = proxy.save(test);
            System.out.println("saved id: " + saved.id);
            System.out.println(proxy.exists(saved.id));
        }
    }

    protected <T> T createRepositoryInstance(Class<T> repositoryClass) {
        Map<Method, Mapper<Object[], Object>> methods = prepareMethods(repositoryClass);
        Object o = Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                (proxy, method, args) -> methods.get(method).map(args));

        return (T) o;
    }

    protected Map<Method, Mapper<Object[], Object>> prepareMethods(Class clazz) {
        Generic generic = new Generic(clazz);
        Class type = generic.getInterface(0).type(0).clazz;
        List<GenericMethod> genericMethods = generic.methods();
        Map<Method, Mapper<Object[], Object>> mappers = new HashMap<>(genericMethods.size() + 1, 1);
        for (GenericMethod gm : genericMethods) {
            if (Modifier.isStatic(gm.method.getModifiers()))
                continue;
            if (gm.method.isDefault())
                continue;

            Mapper<Object[], Object> mapper = createMapper(type, gm);
            mappers.put(gm.method, mapper);
        }
        return mappers;
    }

    protected <T> Mapper<Object[], T> createMapper(Class clazz, GenericMethod genericMethod) {
        //TODO: http://docs.spring.io/spring-data/jpa/docs/1.4.3.RELEASE/reference/html/repositories.html#repositories.query-methods.query-creation
        //TODO: http://docs.spring.io/spring-data/jpa/docs/1.4.3.RELEASE/reference/html/jpa.repositories.html#jpa.query-methods.query-creation
        String name = genericMethod.method.getName();
        List<Generic> args = genericMethod.args;
        Mapper<? super ResultSet, T> typeMapper = getMapper(genericMethod.returnType);
        Mapper<Flow, T> resultMapper = chooseResult(genericMethod.returnType);
        Mapper<Flow<ResultSet>, T> finalMapper = flow -> resultMapper.map(flow.map(typeMapper));

        String[] parts = name.split("By", 2);
        String verb = parts[0];
        String by = parts.length == 2 ? parts[1] : (args.size() == 1 ? "id" : "");
        SqlArguments arguments = prepareArguments(by);

        if (verb.startsWith("find") || verb.startsWith("read") || verb.startsWith("get")) {
            String sql = prepareSelect(clazz).query + arguments;
            return objects -> executeQuery(sql, objects, finalMapper);
        }

        if ("count".equals(name)) {
            String sql = "select count(id) from " + toSqlString(clazz.getSimpleName()) + arguments;
            return objects -> executeQuery(sql, objects, finalMapper);
        }
        if ("delete".equals(name)) {
            String sql = "delete from " + toSqlString(clazz.getSimpleName()) + arguments;
            if (args.size() == 1 && args.get(0).clazz == clazz) {
                Fields<FieldInfo> fields = new Fields<>(clazz);
                SqlSetter<Object> setter = getSetter(clazz, fields.get("id"), 1);
                return objects -> (T) (Integer) executeUpdate(sql, setter, objects[0]);
            } else
                return objects -> (T) (Integer) executeUpdate(sql, objects);
        }
        if ("save".equals(name)) {
            PreparedWriteQuery preparedInsert = createPreparedInsert(clazz);
            Fields<FieldInfo> fields = new Fields<>(clazz);
            FieldInfo id = fields.get("id");
            return objects -> {
                Object object = objects[0];
                id.reflection.setObject(object, executeQuery(preparedInsert.query, preparedInsert.setter, object));
                return (T) object;
            };
        }
        if ("exists".equals(name)) {
            String sql = "select count(id)=1 from " + toSqlString(clazz.getSimpleName()) + arguments;
            return objects -> executeQuery(sql, objects, finalMapper);
        }

        throw new IllegalArgumentException("Cannot create mapper for " + genericMethod);
    }

    protected SqlArguments prepareArguments(String s) {
        SqlArguments arguments = new SqlArguments();

        Pattern pattern = Pattern.compile("And|Or|Between|LessThan|GreaterThan|After|Before|IsNull|IsNotNull|NotNull|Like|NotLike|NotIn|IsNot|Not|Is|In|True|False");
        Matcher matcher = pattern.matcher(s);

        int position = 0;
        while (matcher.find(position)) {
            String operator = matcher.group();
            String field = toSqlString(s.substring(position, matcher.start()));
            switch (operator) {
                case "And":
                    if (!field.isEmpty())
                        arguments.append(SqlOperator.EQUALS, field);
                    arguments.append(SqlOperator.AND);
                    break;
                case "Or":
                    if (!field.isEmpty())
                        arguments.append(SqlOperator.EQUALS, field);
                    arguments.append(SqlOperator.OR);
                    break;
                case "Is":
                    arguments.append(SqlOperator.EQUALS, field);
                    break;
                case "Between":
                    arguments.append(SqlOperator.BETWEEN, field);
                    break;
                case "LessThan":
                    arguments.append(SqlOperator.LESS_THAN, field);
                    break;
                case "GreaterThan":
                    arguments.append(SqlOperator.GREATER_THAN, field);
                    break;
                case "After":
                    arguments.append(SqlOperator.AFTER, field);
                    break;
                case "Before":
                    arguments.append(SqlOperator.BEFORE, field);
                    break;
                case "IsNull":
                    arguments.append(SqlOperator.IS_NULL, field);
                    break;
                case "IsNotNull":
                case "NotNull":
                    arguments.append(SqlOperator.IS_NOT_NULL, field);
                    break;
                case "Like":
                    arguments.append(SqlOperator.LIKE, field);
                    break;
                case "NotLike":
                    arguments.append(SqlOperator.NOT_LIKE, field);
                    break;
                case "IsNot":
                case "Not":
                    arguments.append(SqlOperator.IS_NOT, field);
                    break;
                case "In":
                    arguments.append(SqlOperator.IN, field);
                    break;
                case "NotIn":
                    arguments.append(SqlOperator.NOT_IN, field);
                    break;
                case "True":
                    arguments.append(SqlOperator.TRUE, field);
                    break;
                case "False":
                    arguments.append(SqlOperator.FALSE, field);
                    break;
                default:
                    throw new IllegalArgumentException("Operator " + operator + " is not supported");
            }

            position = matcher.end();
        }
        if (position < s.length())
            arguments.append(SqlOperator.EQUALS, toSqlString(s.substring(position)));

        return arguments;
    }

    static class SqlArguments {
        List<Pair<SqlOperator, String>> arguments = new ArrayList<>();

        public SqlArguments append(SqlOperator operator, String field) {
            arguments.add(new Pair<>(operator, field));
            return this;
        }

        public SqlArguments append(SqlOperator operator) {
            return append(operator, null);
        }

        public void build(StringBuilder sb) {
            if (arguments.isEmpty())
                return;

            sb.append(" where ");
            for (Pair<SqlOperator, String> pair : arguments) {
                pair.key.builder.consume(sb, pair.value);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            build(sb);
            return sb.toString();
        }
    }

    enum SqlOperator {
        AND((sb, field) -> sb.append(" and ")),
        OR((sb, field) -> sb.append(" or ")),
        EQUALS((sb, field) -> sb.append(field).append("=?")),
        LESS_THAN((sb, field) -> sb.append(field).append("<?")),
        GREATER_THAN((sb, field) -> sb.append(field).append(">?")),
        AFTER((sb, field) -> sb.append(field).append(">?")),
        BEFORE((sb, field) -> sb.append(field).append("<?")),
        IS_NULL((sb, field) -> sb.append(field).append(" is null")),
        IS_NOT_NULL((sb, field) -> sb.append(field).append(" not null")),
        LIKE((sb, field) -> sb.append(field).append(" like ?")),
        NOT_LIKE((sb, field) -> sb.append(field).append(" not like ?")),
        IN((sb, field) -> sb.append(field).append(" in ?")),
        NOT_IN((sb, field) -> sb.append(field).append(" not in ?")),
        IS_NOT((sb, field) -> sb.append(field).append("<>?")),
        BETWEEN((sb, field) -> sb.append(field).append(" between ? and ?")),
        TRUE((sb, field) -> sb.append(field).append("=true")),
        FALSE((sb, field) -> sb.append(field).append("=false")),
        NOOP((sb, field) -> {
        }),;

        final BiConsumer<StringBuilder, String> builder;

        SqlOperator(BiConsumer<StringBuilder, String> builder) {
            this.builder = builder;
        }
    }
}
