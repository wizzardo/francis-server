package com.wizzardo.francis.services.orm;

import com.wizzardo.tools.interfaces.BiConsumer;

/**
 * Created by wizzardo on 28/03/17.
 */
public enum SqlOperator {
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
