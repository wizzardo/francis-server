package com.wizzardo.francis.services.orm;

import com.wizzardo.tools.interfaces.BiConsumer;

/**
 * Created by wizzardo on 28/03/17.
 */
public enum SqlOperator {
    AND((sb, field) -> sb.append(" and ")),
    OR((sb, field) -> sb.append(" or ")),
    EQUALS(1, (sb, field) -> sb.append(field).append("=?")),
    LESS_THAN(1, (sb, field) -> sb.append(field).append("<?")),
    GREATER_THAN(1, (sb, field) -> sb.append(field).append(">?")),
    AFTER(1, (sb, field) -> sb.append(field).append(">?")),
    BEFORE(1, (sb, field) -> sb.append(field).append("<?")),
    IS_NULL(1, (sb, field) -> sb.append(field).append(" is null")),
    IS_NOT_NULL((sb, field) -> sb.append(field).append(" not null")),
    LIKE(1, (sb, field) -> sb.append(field).append(" like ?")),
    NOT_LIKE(1, (sb, field) -> sb.append(field).append(" not like ?")),
    IN(1, (sb, field) -> sb.append(field).append(" in (?)")),
    NOT_IN(1, (sb, field) -> sb.append(field).append(" not in (?)")),
    IS_NOT(1, (sb, field) -> sb.append(field).append("<>?")),
    BETWEEN(2, (sb, field) -> sb.append(field).append(" between ? and ?")),
    TRUE((sb, field) -> sb.append(field).append("=true")),
    FALSE((sb, field) -> sb.append(field).append("=false")),
    NOOP((sb, field) -> {
    }),;

    final BiConsumer<StringBuilder, String> builder;
    final int args;

    SqlOperator(BiConsumer<StringBuilder, String> builder) {
        this(0, builder);
    }

    SqlOperator(int args, BiConsumer<StringBuilder, String> builder) {
        this.args = args;
        this.builder = builder;
    }
}
