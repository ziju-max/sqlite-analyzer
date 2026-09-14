package org.csu.sqliteanalyzer.analyzer.logical_plan;

import org.csu.sqliteanalyzer.analyzer.common.Assignment;

import java.util.List;

/**
 * 更新执行计划节点
 */
public class UpdateNode extends TreeRootNode {
    private String tableName;
    private List<Assignment> assignments;

    public UpdateNode(String tableName, List<Assignment> assignments, TreeRootNode child) {
        super(List.of(child));
        this.tableName = tableName;
        this.assignments = assignments;
    }

    public String getTableName() {
        return tableName;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    @Override
    public String getNodeType() {
        return "UPDATE";
    }

    @Override
    public String toString() {
        return String.format("Update(table=%s, assignments=%s)", tableName, assignments);
    }
}
