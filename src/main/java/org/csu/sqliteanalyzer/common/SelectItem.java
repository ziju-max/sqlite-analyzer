package org.csu.sqliteanalyzer.common;

import java.util.Optional;

/**
 * SELECT列表项，支持列别名
 * 对应文法: column_name [ AS IDENTIFIER ]
 */
public class SelectItem {
    private String columnName;
    private String alias;

    public SelectItem(String columnName, String alias) {
        this.columnName = columnName;
        this.alias = alias;
    }

    public String getColumnName() {
        return columnName;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    @Override
    public String toString() {
        return alias != null ? String.format("%s AS %s", columnName, alias) : columnName;
    }
}
