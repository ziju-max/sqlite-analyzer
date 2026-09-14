package org.csu.sqliteanalyzer.logical_plan.select;

import org.csu.sqliteanalyzer.logical_plan.TreeRootNode;

import java.util.List;

/**
 * 列投影执行计划节点
 */
public class ProjectNode extends TreeRootNode {
    private List<String> columns;

    public ProjectNode(List<String> columns, TreeRootNode child) {
        super(List.of(child));
        this.columns = columns;
    }

    public List<String> getColumns() {
        return columns;
    }

    @Override
    public String getNodeType() {
        return "PROJECT";
    }

    @Override
    public String toString() {
        return String.format("Project(columns=%s)", columns);
    }
}
