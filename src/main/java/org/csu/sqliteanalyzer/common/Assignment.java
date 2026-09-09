package org.csu.sqliteanalyzer.common;

/**
 * 赋值表达式，用于UPDATE语句
 * 对应文法: column_name EQUALS literal
 */
public class Assignment {
    private String columnName;
    private String value;

    public Assignment(String columnName, String value) {
        this.columnName = columnName;
        this.value = value;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("%s=%s", columnName, value);
    }
}
