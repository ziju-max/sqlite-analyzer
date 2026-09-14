package org.csu.sqliteanalyzer.logical_plan;

import java.util.List;

/**
 * 表扫描执行计划节点
 */
public class TableScanNode extends TreeRootNode {
    private String tableName;

    public TableScanNode(String tableName) {
        super(List.of());
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String getNodeType() {
        return "TABLE SCAN";
    }

    @Override
    public String toString() {
        return String.format("TableScan(table=%s)", tableName);
    }
}
