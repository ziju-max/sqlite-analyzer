package org.csu.sqliteanalyzer.logical_plan.filter;

import java.util.List;
import java.util.Objects;

/**
 * OR 表达式节点。多个 OR 按左结合方式组织为二叉树。
 */
public class OrExpr extends BooleanExpression {
    private final BooleanExpression left;
    private final BooleanExpression right;

    public OrExpr(BooleanExpression left, BooleanExpression right) {
        this.left = Objects.requireNonNull(left, "left OR expression must not be null");
        this.right = Objects.requireNonNull(right, "right OR expression must not be null");
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
        return left.toSQL() + " OR " + right.toSQL();
    }

    @Override
    public String toTreeString(String childIndent) {
        StringBuilder tree = new StringBuilder("OrExpr");
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
