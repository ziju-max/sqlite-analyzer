package org.csu.sqliteanalyzer.ast;

/**
 * 抽象语法树节点的基类
 * 所有具体的SQL语法节点都继承自此抽象类
 */
public abstract class ASTNode {
    /**
     * 获取节点类型名称
     * @return 节点类型字符串
     */
    public abstract String getNodeType();

    /**
     * 将AST节点转换回SQL语句
     * @return SQL字符串
     */
    public abstract String toSQL();

    /**
     * 获取节点的描述信息
     * @return 描述字符串
     */
    @Override
    public abstract String toString();
}
