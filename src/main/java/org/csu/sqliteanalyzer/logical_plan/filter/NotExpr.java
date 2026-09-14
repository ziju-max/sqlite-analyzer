package org.csu.sqliteanalyzer.logical_plan.filter;

import java.util.List;
import java.util.Objects;

/**
 * 一元 NOT 表达式节点。
 */
public class NotExpr extends BooleanExpression {
    private final BooleanExpression expression;

    public NotExpr(BooleanExpression expression) {
        this.expression = Objects.requireNonNull(expression, "NOT expression must not be null");
    }

    public BooleanExpression getExpression() {
        return expression;
    }

    @Override
    public List<BooleanExpression> getChildren() {
        return List.of(expression);
    }

    @Override
    public String toSQL() {
        return "NOT " + expression.toSQL();
    }

    @Override
    public String toTreeString(String childIndent) {
        return "NotExpr\n"
                + childIndent
                + "└── "
                + expression.toTreeString(childIndent + "    ");
    }

    @Override
    public String toString() {
        return toTreeString("");
    }
}
