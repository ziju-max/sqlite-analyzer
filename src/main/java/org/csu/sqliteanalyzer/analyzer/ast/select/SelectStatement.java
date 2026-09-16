package org.csu.sqliteanalyzer.analyzer.ast.select;

import java.util.List;
import java.util.Optional;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.common.SelectItem;

/**
 * SELECT语句的AST节点
 * 对应文法: SELECT select_list FROM table_name
 * [ where_clause ] [ group_by_clause ] [ order_by_clause ]
 */
public class SelectStatement extends ASTNode {
    private List<Object> selectList;  // 元素为String、SelectItem或AggregateFunction
    private String tableName;
    private List<JoinClause> joinClauses;
    private String whereClause;
    private GroupByClause groupByClause;
    private OrderByClause orderByClause;

    public SelectStatement(List<Object> selectList, String tableName, String whereClause) {
        this(selectList, tableName, List.of(), whereClause, null, null);
    }

    public SelectStatement(
            List<Object> selectList,
            String tableName,
            String whereClause,
            String orderByClause
    ) {
        this(selectList, tableName, List.of(), whereClause, null, orderByClause);
    }

    public SelectStatement(
            List<Object> selectList,
            String tableName,
            String whereClause,
            String groupByClause,
            String orderByClause
    ) {
        this(selectList, tableName, List.of(), whereClause, groupByClause, orderByClause);
    }

    public SelectStatement(
            List<Object> selectList,
            String tableName,
            List<JoinClause> joinClauses,
            String whereClause,
            String groupByClause,
            String orderByClause
    ) {
        this.selectList = selectList;
        this.tableName = tableName;
        this.joinClauses = List.copyOf(joinClauses);
        this.whereClause = whereClause;
        this.groupByClause = groupByClause == null ? null : new GroupByClause(groupByClause);
        this.orderByClause = orderByClause == null ? null : new OrderByClause(orderByClause);
    }

    public List<Object> getSelectList() {
        return selectList;
    }

    public String getTableName() {
        return tableName;
    }

    public List<JoinClause> getJoins() {
        return joinClauses;
    }

    public Optional<String> getWhereClause() {
        return Optional.ofNullable(whereClause);
    }

    public Optional<String> getGroupByClause() {
        return Optional.ofNullable(groupByClause).map(GroupByClause::getGroupInfo);
    }

    public Optional<GroupByClause> getGroupBy() {
        return Optional.ofNullable(groupByClause);
    }

    public Optional<String> getOrderByClause() {
        return Optional.ofNullable(orderByClause).map(OrderByClause::getSortInfo);
    }

    public Optional<OrderByClause> getOrderBy() {
        return Optional.ofNullable(orderByClause);
    }

    @Override
    public String getNodeType() {
        return "SELECT";
    }

    @Override
    public String toSQL() {
        StringBuilder sql = new StringBuilder("SELECT ");

        if (selectList.size() == 1 && selectList.get(0) instanceof String && "*".equals(selectList.get(0))) {
            sql.append("*");
        } else {
            for (int i = 0; i < selectList.size(); i++) {
                if (i > 0) sql.append(", ");
                Object item = selectList.get(i);
                if (item instanceof String) {
                    sql.append(item);
                } else if (item instanceof SelectItem) {
                    sql.append(item);
                } else if (item instanceof AggregateFunction) {
                    sql.append(item);
                }
            }
        }

        sql.append(" FROM ").append(tableName);
        for (JoinClause joinClause : joinClauses) {
            sql.append(" ").append(joinClause.toSQL());
        }

        if (whereClause != null) {
            sql.append(" WHERE ").append(whereClause);
        }

        if (groupByClause != null) {
            sql.append(" GROUP BY ").append(groupByClause);
        }

        if (orderByClause != null) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return sql.toString();
    }

    @Override
    public String toString() {
        return renderTree(
                "SelectStatement (SELECT)",
                treeProperty("selectList", selectList),
                treeProperty("tableName", tableName),
                treeProperty("joins", joinClauses),
                whereClauseTree(whereClause),
                treeProperty("groupByClause", groupByClause),
                treeProperty("orderByClause", orderByClause)
        );
    }
}
