package org.csu.sqliteanalyzer.analyzer.ast;

import org.csu.sqliteanalyzer.analyzer.exception.PlanException;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.WhereClauseExpressionParser;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

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

    protected final String renderTree(String rootLabel, TreeEntry... entries) {
        StringBuilder tree = new StringBuilder(rootLabel);
        int presentEntryCount = 0;
        for (TreeEntry entry : entries) {
            if (entry != null) {
                presentEntryCount++;
            }
        }

        int entryIndex = 0;
        for (TreeEntry entry : entries) {
            if (entry == null) {
                continue;
            }

            boolean last = entryIndex == presentEntryCount - 1;
            String branchIndent = last ? "    " : "│   ";
            tree.append("\n")
                    .append(last ? "└── " : "├── ")
                    .append(entry.label);

            if (entry.childRenderer != null) {
                String childTree = entry.childRenderer.apply(branchIndent + "    ");
                if (childTree != null && !childTree.isBlank()) {
                    tree.append("\n")
                            .append(branchIndent)
                            .append("└── ")
                            .append(childTree);
                }
            }
            entryIndex++;
        }
        return tree.toString();
    }

    protected final TreeEntry treeProperty(String name, Object value) {
        return isEmpty(value) ? null : new TreeEntry(name + ": " + value, null);
    }

    protected final TreeEntry whereClauseTree(String whereClause) {
        if (isEmpty(whereClause)) {
            return null;
        }
        return new TreeEntry("whereClause", childIndent -> renderWhereClause(whereClause, childIndent));
    }

    private String renderWhereClause(String whereClause, String childIndent) {
        try {
            BooleanExpression expression = new WhereClauseExpressionParser(
                    new LexerService().tokenize(whereClause)
            ).parse();
            return expression.toTreeString(childIndent);
        } catch (PlanException | RuntimeException exception) {
            return whereClause;
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Optional<?> optional) {
            return optional.isEmpty();
        }
        return false;
    }

    protected static final class TreeEntry {
        private final String label;
        private final Function<String, String> childRenderer;

        private TreeEntry(String label, Function<String, String> childRenderer) {
            this.label = label;
            this.childRenderer = childRenderer;
        }
    }
}
