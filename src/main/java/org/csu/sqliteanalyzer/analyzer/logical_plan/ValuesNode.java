package org.csu.sqliteanalyzer.analyzer.logical_plan;

import java.util.List;

/**
 * 插入值执行计划节点
 */
public class ValuesNode extends TreeRootNode {
    private List<String> values;

    public ValuesNode(List<String> values) {
        super(List.of());
        this.values = values;
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public String getNodeType() {
        return "VALUES";
    }

    @Override
    public String toString() {
        return String.format("Values(values=%s)", values);
    }
}
