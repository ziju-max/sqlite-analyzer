package org.csu.sqliteanalyzer.analyzer.logical_plan.select;

import org.csu.sqliteanalyzer.analyzer.logical_plan.TreeRootNode;

import java.util.List;
import java.util.Objects;

/**
 * 排序执行计划节点。
 */
public class SortNode extends TreeRootNode {
    private final String sortInfo;

    public SortNode(String sortInfo, TreeRootNode child) {
        super(List.of(Objects.requireNonNull(child, "sort child must not be null")));
        this.sortInfo = requireText(sortInfo);
    }

    public String getSortInfo() {
        return sortInfo;
    }

    @Override
    public String getNodeType() {
        return "SORT";
    }

    @Override
    public String toString() {
        return String.format("Sort(sortInfo=%s)", sortInfo);
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "sort info must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("sort info must not be blank");
        }
        return value;
    }
}
