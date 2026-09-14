package org.csu.sqliteanalyzer.logical_plan;

import java.util.List;

/**
 * 删除执行计划节点
 */
public class DeleteNode extends TreeRootNode {
    private String tableName;

    public DeleteNode(String tableName, TreeRootNode child) {
        super(List.of(child));
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String getNodeType() {
        return "DELETE";
    }

    @Override
    public String toString() {
        return String.format("Delete(table=%s)", tableName);
    }
}
