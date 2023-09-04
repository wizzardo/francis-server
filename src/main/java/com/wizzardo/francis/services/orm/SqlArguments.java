package com.wizzardo.francis.services.orm;

import com.wizzardo.tools.misc.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wizzardo.francis.services.orm.SqlOperator.*;
import static com.wizzardo.francis.services.orm.SqlTools.toSqlString;

/**
 * Created by wizzardo on 28/03/17.
 */
public class SqlArguments {
    public static final Pattern OPERATORS_PATTERN = Pattern.compile("And|Or|Between|LessThan|GreaterThan|After|Before|IsNull|IsNotNull|NotNull|Like|NotLike|NotIn|IsNot|Not|Is|In|True|False");

    static void test(String a, String b) {
        if (!a.equals(b))
            throw new AssertionError(a + " != " + b);
    }

    static {
        test(" where name=? and age=?", prepareArguments("NameAndAge").toString());
        test(" where name=? or age=?", prepareArguments("NameOrAge").toString());
        test(" where name=? or age=?", prepareArguments("NameIsOrAgeIs").toString());
        test(" where age between ? and ?", prepareArguments("AgeBetween").toString());
        test(" where age<?", prepareArguments("AgeLessThan").toString());
        test(" where age>?", prepareArguments("AgeGreaterThan").toString());
        test(" where age<?", prepareArguments("AgeBefore").toString());
        test(" where age>?", prepareArguments("AgeAfter").toString());
        test(" where age is null", prepareArguments("AgeIsNull").toString());
        test(" where age not null", prepareArguments("AgeIsNotNull").toString());
        test(" where age not null", prepareArguments("AgeNotNull").toString());
        test(" where age like ?", prepareArguments("AgeLike").toString());
        test(" where age<>?", prepareArguments("AgeNot").toString());
        test(" where age in (?)", prepareArguments("AgeIn").toString());
        test(" where age not in (?)", prepareArguments("AgeNotIn").toString());
        test(" where active=true", prepareArguments("ActiveTrue").toString());
        test(" where active=false", prepareArguments("ActiveFalse").toString());
    }

    protected List<Pair<SqlOperator, String>> arguments = new ArrayList<>();

    public SqlArguments append(SqlOperator operator, String field) {
        if (field != null && field.isEmpty())
            throw new IllegalArgumentException("Field cannot be empty");

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

    public int count() {
        int args = 0;
        for (Pair<SqlOperator, String> pair : arguments) {
            args += pair.key.args;
        }
        return args;
    }

    public static SqlArguments prepareArguments(String s) {
        SqlArguments arguments = new SqlArguments();

        Matcher matcher = OPERATORS_PATTERN.matcher(s);

        int position = 0;
        while (matcher.find(position)) {
            String operator = matcher.group();
            String field = toSqlString(s.substring(position, matcher.start()));
            switch (operator) {
                case "And":
                    if (!field.isEmpty())
                        arguments.append(EQUALS, field);
                    arguments.append(AND);
                    break;
                case "Or":
                    if (!field.isEmpty())
                        arguments.append(EQUALS, field);
                    arguments.append(OR);
                    break;
                case "Is":
                    arguments.append(EQUALS, field);
                    break;
                case "Between":
                    arguments.append(BETWEEN, field);
                    break;
                case "LessThan":
                    arguments.append(LESS_THAN, field);
                    break;
                case "GreaterThan":
                    arguments.append(GREATER_THAN, field);
                    break;
                case "After":
                    arguments.append(AFTER, field);
                    break;
                case "Before":
                    arguments.append(BEFORE, field);
                    break;
                case "IsNull":
                    arguments.append(IS_NULL, field);
                    break;
                case "IsNotNull":
                case "NotNull":
                    arguments.append(IS_NOT_NULL, field);
                    break;
                case "Like":
                    arguments.append(LIKE, field);
                    break;
                case "NotLike":
                    arguments.append(NOT_LIKE, field);
                    break;
                case "IsNot":
                case "Not":
                    arguments.append(IS_NOT, field);
                    break;
                case "In":
                    arguments.append(IN, field);
                    break;
                case "NotIn":
                    arguments.append(NOT_IN, field);
                    break;
                case "True":
                    arguments.append(TRUE, field);
                    break;
                case "False":
                    arguments.append(FALSE, field);
                    break;
                default:
                    throw new IllegalArgumentException("Operator " + operator + " is not supported");
            }

            position = matcher.end();
        }
        if (position < s.length())
            arguments.append(EQUALS, toSqlString(s.substring(position)));

        return arguments;
    }
}
