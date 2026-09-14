package org.csu.sqliteanalyzer.services;

import org.csu.sqliteanalyzer.logical_plan.filter.FilterNode;
import org.csu.sqliteanalyzer.logical_plan.TreeRoot;
import org.csu.sqliteanalyzer.logical_plan.TreeRootNode;
import org.csu.sqliteanalyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.logical_plan.filter.ComparisonExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 执行计划树形文本输出服务
 */
public class TreeTextService {

    /**
     * 将执行计划输出为树形文本。
     *
     * @param plan 执行计划
     * @return 树形文本
     */
    public String toText(TreeRoot plan) {
        Objects.requireNonNull(plan, "logical plan must not be null");
        return toText(plan.getRoot());
    }

    /**
     * 将执行计划根节点输出为树形文本。
     *
     * @param root 执行计划根节点
     * @return 树形文本
     */
    public String toText(TreeRootNode root) {
        Objects.requireNonNull(root, "logical plan root must not be null");

        StringBuilder text = new StringBuilder();
        appendNode(text, toTextNode(root), "", true, true);
        return text.toString();
    }

    private void appendNode(
            StringBuilder text,
            TextNode node,
            String prefix,
            boolean isLast,
            boolean root
    ) {
        if (root) {
            text.append(node.label);
        } else {
            text.append(prefix)
                    .append(isLast ? "└── " : "├── ")
                    .append(node.label);
        }

        List<TextNode> children = node.children;
        for (int index = 0; index < children.size(); index++) {
            text.append("\n");
            TextNode child = children.get(index);
            boolean childIsLast = index == children.size() - 1;
            String childPrefix = root
                    ? ""
                    : prefix + (isLast ? "    " : "│   ");
            appendNode(text, child, childPrefix, childIsLast, false);
        }
    }

    private TextNode toTextNode(TreeRootNode node) {
        if (node instanceof FilterNode filter) {
            List<TextNode> children = new ArrayList<>();
            children.add(toTextNode(filter.getExpression()));
            children.addAll(node.getChildren().stream()
                    .map(this::toTextNode)
                    .toList());
            return new TextNode("FilterStatement", children);
        }

        return new TextNode(
                node.toString(),
                node.getChildren().stream()
                        .map(this::toTextNode)
                        .toList()
        );
    }

    private TextNode toTextNode(BooleanExpression expression) {
        String label = expression instanceof ComparisonExpr
                ? expression.toString()
                : expression.getClass().getSimpleName();

        return new TextNode(
                label,
                expression.getChildren().stream()
                        .map(this::toTextNode)
                        .toList()
        );
    }

    private static final class TextNode {
        private final String label;
        private final List<TextNode> children;

        private TextNode(String label, List<TextNode> children) {
            this.label = label;
            this.children = children;
        }
    }
}
