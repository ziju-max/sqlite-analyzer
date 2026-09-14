package org.csu.sqliteanalyzer.services;

import org.csu.sqliteanalyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.logical_plan.DeleteNode;
import org.csu.sqliteanalyzer.logical_plan.select.AggregateFunctionNode;
import org.csu.sqliteanalyzer.logical_plan.filter.FilterNode;
import org.csu.sqliteanalyzer.logical_plan.select.GroupByNode;
import org.csu.sqliteanalyzer.logical_plan.InsertNode;
import org.csu.sqliteanalyzer.logical_plan.select.ProjectNode;
import org.csu.sqliteanalyzer.logical_plan.select.SortNode;
import org.csu.sqliteanalyzer.logical_plan.TableScanNode;
import org.csu.sqliteanalyzer.logical_plan.TreeRoot;
import org.csu.sqliteanalyzer.logical_plan.TreeRootNode;
import org.csu.sqliteanalyzer.logical_plan.UpdateNode;
import org.csu.sqliteanalyzer.logical_plan.ValuesNode;
import org.csu.sqliteanalyzer.logical_plan.filter.AndExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.logical_plan.filter.ComparisonExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.NotExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.OrExpr;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将 AstTreeService 生成的 TreeRoot 转换为结构化执行计划文本。
 */
public class LogicalPlanTextService {

    /**
     * 输出结构化执行计划。
     *
     * @param treeRoot AstTreeService 生成的树
     * @return 结构化执行计划文本
     */
    public String toText(TreeRoot treeRoot) {
        Objects.requireNonNull(treeRoot, "tree root must not be null");
        Objects.requireNonNull(treeRoot.getRoot(), "tree root node must not be null");

        String tableName = findTableName(treeRoot.getRoot());
        StringBuilder plan = new StringBuilder();
        appendNode(plan, treeRoot.getRoot(), tableName, 0);
        return plan.toString();
    }

    /**
     * 输出结构化执行计划。与 {@link #toText(TreeRoot)} 等价。
     *
     * @param treeRoot AstTreeService 生成的树
     * @return 结构化执行计划文本
     */
    public String generate(TreeRoot treeRoot) {
        return toText(treeRoot);
    }

    private void appendNode(
            StringBuilder plan,
            TreeRootNode node,
            String tableName,
            int level
    ) {
        plan.append("    ".repeat(level))
                .append("-> ")
                .append(formatNode(node, tableName));

        for (TreeRootNode child : node.getChildren()) {
            plan.append("\n");
            appendNode(plan, child, tableName, level + 1);
        }
    }

    private String formatNode(TreeRootNode node, String tableName) {
        if (node instanceof ProjectNode project) {
            String columns = project.getColumns().stream()
                    .map(column -> renderProjectionColumn(column, tableName))
                    .collect(Collectors.joining(", "));
            return "Project: " + columns;
        }

        if (node instanceof FilterNode filter) {
            return "Filter: " + renderExpression(filter.getExpression(), tableName, false);
        }

        if (node instanceof AggregateFunctionNode aggregate) {
            return "Aggregate: " + aggregate.getAggregateFunctions().stream()
                    .map(function -> renderAggregateInfo(function, tableName))
                    .collect(Collectors.joining(", "));
        }

        if (node instanceof GroupByNode groupBy) {
            return "Group by: " + renderGroupInfo(groupBy.getGroupInfo(), tableName);
        }

        if (node instanceof SortNode sort) {
            return "Sort: " + renderSortInfo(sort.getSortInfo(), tableName);
        }

        if (node instanceof TableScanNode tableScan) {
            return "Table scan on " + tableScan.getTableName();
        }

        if (node instanceof InsertNode insert) {
            return "Insert into " + insert.getTableName()+insert.getColumns();
        }

        if (node instanceof ValuesNode values) {
            return "Values: " + values.getValues();
        }

        if (node instanceof UpdateNode update) {
            return "Update: " + update.getTableName();
        }

        if (node instanceof DeleteNode delete) {
            return "Delete from " + delete.getTableName();
        }

        return node.toString();
    }

    private String renderExpression(
            BooleanExpression expression,
            String tableName,
            boolean wrap
    ) {
        String rendered;

        if (expression instanceof ComparisonExpr comparison) {
            rendered = qualifyColumn(comparison.getLeftOperand(), tableName)
                    + " " + comparison.getOperator()
                    + " " + comparison.getRightOperand();
        } else if (expression instanceof OrExpr or) {
            rendered = renderExpression(or.getLeft(), tableName, true)
                    + " or "
                    + renderExpression(or.getRight(), tableName, true);
        } else if (expression instanceof AndExpr and) {
            rendered = renderExpression(and.getLeft(), tableName, true)
                    + " and "
                    + renderExpression(and.getRight(), tableName, true);
        } else if (expression instanceof NotExpr not) {
            rendered = "not " + renderExpression(not.getExpression(), tableName, true);
        } else {
            rendered = expression.toSQL();
        }

        return wrap ? "(" + rendered + ")" : rendered;
    }

    private String renderProjectionColumn(String column, String tableName) {
        if (column == null) {
            return null;
        }

        String trimmedColumn = column.trim();
        int openingParenthesis = trimmedColumn.indexOf('(');
        if (openingParenthesis > 0 && trimmedColumn.endsWith(")")) {
            String functionType = trimmedColumn.substring(0, openingParenthesis);
            String identifier = trimmedColumn.substring(
                    openingParenthesis + 1,
                    trimmedColumn.length() - 1
            );
            return renderAggregateInfo(functionType, identifier, tableName);
        }
        return qualifyColumn(column, tableName);
    }

    private String renderAggregateInfo(
            AggregateFunction function,
            String tableName
    ) {
        return renderAggregateInfo(
                function.getFunctionType(),
                function.getIdentifier(),
                tableName
        );
    }

    private String renderAggregateInfo(
            String functionType,
            String identifier,
            String tableName
    ) {
        return functionType + "(" + qualifyColumn(identifier, tableName) + ")";
    }

    private String renderGroupInfo(String groupInfo, String tableName) {
        return java.util.Arrays.stream(groupInfo.split(","))
                .map(String::trim)
                .map(groupColumn -> qualifyColumn(groupColumn, tableName))
                .collect(Collectors.joining(", "));
    }

    private String qualifyColumn(String column, String tableName) {
        if (tableName == null
                || column == null
                || column.isBlank()
                || "*".equals(column)
                || column.contains(".")) {
            return column;
        }
        return tableName + "." + column;
    }

    private String renderSortInfo(String sortInfo, String tableName) {
        return java.util.Arrays.stream(sortInfo.split(","))
                .map(String::trim)
                .map(sortItem -> qualifyColumn(sortItem, tableName))
                .collect(Collectors.joining(", "));
    }

    private String findTableName(TreeRootNode node) {
        if (node instanceof TableScanNode tableScan) {
            return tableScan.getTableName();
        }

        for (TreeRootNode child : node.getChildren()) {
            String tableName = findTableName(child);
            if (tableName != null) {
                return tableName;
            }
        }
        return null;
    }
}
