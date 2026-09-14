package org.csu.sqliteanalyzer.logical_plan;

import java.util.List;

/**
 * 插入执行计划节点
 */
public class InsertNode extends TreeRootNode {
    private String tableName;
    private List<String> columns;

    public InsertNode(String tableName, List<String> columns, TreeRootNode child) {
        super(List.of(child));
        this.tableName = tableName;
        this.columns = columns;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    @Override
    public String getNodeType() {
        return "INSERT";
    }

    @Override
    public String toString() {
        return String.format("Insert(table=%s, columns=%s)", tableName, columns);
    }
}
