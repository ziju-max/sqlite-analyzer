package org.csu.sqliteanalyzer.analyzer.logical_plan.filter;

import java.util.List;
import java.util.Objects;

/**
 * 比较表达式节点，例如 age > 18。
 */
public class ComparisonExpr extends BooleanExpression {
    private final String leftOperand;
    private final String operator;
    private final String rightOperand;

    public ComparisonExpr(String leftOperand, String operator, String rightOperand) {
        this.leftOperand = requireText(leftOperand, "left operand");
        this.operator = requireText(operator, "comparison operator");
        this.rightOperand = requireText(rightOperand, "right operand");
    }

    public String getLeftOperand() {
        return leftOperand;
    }

    public String getOperator() {
        return operator;
    }

    public String getRightOperand() {
        return rightOperand;
    }

    @Override
    public List<BooleanExpression> getChildren() {
        return List.of();
    }

    @Override
    public String toSQL() {
        return leftOperand + " " + operator + " " + rightOperand;
    }

    @Override
    public String toTreeString(String childIndent) {
        return String.format("ComparisonExpr(%s, %s, %s)",
                leftOperand, operator, rightOperand);
    }

    @Override
    public String toString() {
        return toTreeString("");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
