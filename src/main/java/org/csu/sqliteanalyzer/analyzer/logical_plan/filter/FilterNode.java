package org.csu.sqliteanalyzer.analyzer.logical_plan.filter;

import org.csu.sqliteanalyzer.analyzer.logical_plan.TreeRootNode;

import java.util.List;
import java.util.Objects;

/**
 * 条件过滤执行计划节点
 */
public class FilterNode extends TreeRootNode {
    private final String condition;
    private final BooleanExpression expression;

    public FilterNode(String condition, BooleanExpression expression, TreeRootNode child) {
        super(List.of(child));
        this.condition = requireText(condition, "filter condition");
        this.expression = Objects.requireNonNull(expression, "filter expression must not be null");
    }

    public String getCondition() {
        return condition;
    }

    public BooleanExpression getExpression() {
        return expression;
    }

    public String toExpressionTreeString() {
        return "FilterPlanNode\n└── " + expression.toTreeString("    ");
    }

    @Override
    public String getNodeType() {
        return "FILTER";
    }

    @Override
    public String toString() {
        return "FilterPlanNode\n└── " + expression.toTreeString("    ");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
