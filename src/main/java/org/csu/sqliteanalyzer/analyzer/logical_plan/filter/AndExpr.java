package org.csu.sqliteanalyzer.analyzer.logical_plan.filter;

import java.util.List;
import java.util.Objects;

/**
 * AND 表达式节点。多个 AND 按左结合方式组织为二叉树。
 */
public class AndExpr extends BooleanExpression {
    private final BooleanExpression left;
    private final BooleanExpression right;

    public AndExpr(BooleanExpression left, BooleanExpression right) {
        this.left = Objects.requireNonNull(left, "left AND expression must not be null");
        this.right = Objects.requireNonNull(right, "right AND expression must not be null");
    }

    public BooleanExpression getLeft() {
        return left;
    }

    public BooleanExpression getRight() {
        return right;
    }

    @Override
    public List<BooleanExpression> getChildren() {
        return List.of(left, right);
    }

    @Override
    public String toSQL() {
        return left.toSQL() + " AND " + right.toSQL();
    }

    @Override
    public String toTreeString(String childIndent) {
        StringBuilder tree = new StringBuilder("AndExpr");
        tree.append("\n")
                .append(childIndent)
                .append("├── ")
                .append(left.toTreeString(childIndent + "│   "));
        tree.append("\n")
                .append(childIndent)
                .append("└── ")
                .append(right.toTreeString(childIndent + "    "));
        return tree.toString();
    }

    @Override
    public String toString() {
        return toTreeString("");
    }
}
