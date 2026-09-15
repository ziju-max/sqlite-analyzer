package org.csu.sqliteanalyzer.engine.metadata;

import java.util.List;

/**
 * 一张表的完整元数据：表名、列列表、根页号（该表第一个数据页）。
 */
public class TableInfo {
    private final String tableName;     // 表名
    private final List<Column> columns; // 列列表（顺序固定）
    private final int rootPageId;       // 首个数据页的页号

    public TableInfo(String tableName, List<Column> columns, int rootPageId) {
        this.tableName = tableName;
        this.columns = columns;
        this.rootPageId = rootPageId;
    }

    public String getTableName() { return tableName; }
    public List<Column> getColumns() { return columns; }
    public int getRootPageId() { return rootPageId; }

    /**
     * 根据列名找列，找不到返回 null。
     */
    public Column getColumnByName(String name) {
        for (Column col : columns) {
            if (col.getName().equalsIgnoreCase(name)) {
                return col;
            }
        }
        return null;
    }
}
