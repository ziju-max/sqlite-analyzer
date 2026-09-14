package org.csu.sqliteanalyzer.analyzer.logical_plan;

import java.util.List;

/**
 * 执行计划节点的基类
 * 所有具体的执行计划节点都继承自此抽象类
 */
public abstract class TreeRootNode {
    private List<TreeRootNode> children;

    protected TreeRootNode(List<TreeRootNode> children) {
        this.children = children;
    }

    public List<TreeRootNode> getChildren() {
        return children;
    }

    /**
     * 获取执行计划节点类型名称
     * @return 节点类型字符串
     */
    public abstract String getNodeType();
}
