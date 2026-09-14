package org.csu.sqliteanalyzer.analyzer.logical_plan.filter;

import java.util.List;

/**
 * WHERE 条件在执行计划阶段解析后的布尔表达式基类。
 */
public abstract class BooleanExpression {
    /**
     * 返回表达式树的直接子节点，便于遍历执行计划过滤条件。
     */
    public abstract List<BooleanExpression> getChildren();

    /**
     * 将表达式转换回 SQL 条件片段。
     */
    public abstract String toSQL();

    /**
     * 返回当前表达式节点的树形文本。
     *
     * @param childIndent 子节点缩进前缀
     * @return 树形文本
     */
    public abstract String toTreeString(String childIndent);
}
