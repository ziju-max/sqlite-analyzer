package org.csu.sqliteanalyzer.analyzer.logical_plan;

/**
 * SQL执行计划
 * 保存执行计划的根节点并以树形结构输出
 */
public class TreeRoot {
    private TreeRootNode root;

    public TreeRoot(TreeRootNode root) {
        this.root = root;
    }

    public TreeRootNode getRoot() {
        return root;
    }

    @Override
    public String toString() {
        StringBuilder plan = new StringBuilder();
        appendNode(plan, root, 0);
        return plan.toString().trim();
    }

    private void appendNode(StringBuilder plan, TreeRootNode node, int level) {
        plan.append("  ".repeat(level));
        plan.append(node).append("\n");

        for (TreeRootNode child : node.getChildren()) {
            appendNode(plan, child, level + 1);
        }
    }
}
