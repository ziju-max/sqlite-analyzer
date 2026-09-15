package org.csu.sqliteanalyzer.engine.metadata;

/**
 * 表的列定义：列名 + 数据类型。
 */
public class Column {
    private final String name;
    private final DataType type;

    public Column(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public DataType getType() { return type; }

    @Override
    public String toString() {
        return name + " " + type;
    }
}
