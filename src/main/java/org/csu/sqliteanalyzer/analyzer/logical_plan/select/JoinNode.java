package org.csu.sqliteanalyzer.analyzer.logical_plan.select;

import java.util.List;
import java.util.Optional;

import org.csu.sqliteanalyzer.analyzer.ast.select.JoinClause;
import org.csu.sqliteanalyzer.analyzer.logical_plan.TreeRootNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.BooleanExpression;

/**
 * JOIN 的逻辑计划节点，左、右子节点分别表示参与连接的两个输入。
 */
public class JoinNode extends TreeRootNode {
    private final String joinType;
    private final String tableName;
    private final String condition;
    private final BooleanExpression expression;

    public JoinNode(
            JoinClause joinClause,
            BooleanExpression expression,
            TreeRootNode leftChild,
            TreeRootNode rightChild
    ) {
        super(List.of(leftChild, rightChild));
        this.joinType = joinClause.getJoinType();
        this.tableName = joinClause.getTableName();
        this.condition = joinClause.getCondition().orElse(null);
        this.expression = expression;
    }

    public String getJoinType() {
        return joinType;
    }

    public String getTableName() {
        return tableName;
    }

    public Optional<String> getCondition() {
        return Optional.ofNullable(condition);
    }

    public Optional<BooleanExpression> getExpression() {
        return Optional.ofNullable(expression);
    }

    public TreeRootNode getLeftChild() {
        return getChildren().get(0);
    }

    public TreeRootNode getRightChild() {
        return getChildren().get(1);
    }

    @Override
    public String getNodeType() {
        return "JOIN";
    }

    @Override
    public String toString() {
        return String.format(
                "JoinNode{joinType='%s', tableName='%s', condition=%s}",
                joinType, tableName, condition);
    }
}
