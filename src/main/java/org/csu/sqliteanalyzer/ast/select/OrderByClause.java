package org.csu.sqliteanalyzer.ast.select;

import java.util.Objects;

/**
 * ORDER BY 子句的AST对象。
 * 排序字段及方向以原始字符串形式保存。
 */
public class OrderByClause {
    private final String sortInfo;

    public OrderByClause(String sortInfo) {
        this.sortInfo = requireText(sortInfo);
    }

    public String getSortInfo() {
        return sortInfo;
    }

    @Override
    public String toString() {
        return sortInfo;
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "sort info must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("sort info must not be blank");
        }
        return value;
    }
}
