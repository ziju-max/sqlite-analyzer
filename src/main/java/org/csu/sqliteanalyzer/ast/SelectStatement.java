package org.csu.sqliteanalyzer.ast;

import java.util.List;
import java.util.Optional;
import org.csu.sqliteanalyzer.common.SelectItem;

/**
 * SELECT语句的AST节点
 * 对应文法: SELECT select_list FROM table_name [ where_clause ]
 */
public class SelectStatement extends ASTNode {
    private List<Object> selectList;  // 元素为String或SelectItem
    private String tableName;
    private String whereClause;

    public SelectStatement(List<Object> selectList, String tableName, String whereClause) {
        this.selectList = selectList;
        this.tableName = tableName;
        this.whereClause = whereClause;
    }

    public List<Object> getSelectList() {
        return selectList;
    }

    public String getTableName() {
        return tableName;
    }

    public Optional<String> getWhereClause() {
        return Optional.ofNullable(whereClause);
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
                }
            }
        }

        sql.append(" FROM ").append(tableName);

        if (whereClause != null) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    @Override
    public String toString() {
        return String.format("SelectStatement{selectList=%s, tableName='%s', whereClause=%s}",
                selectList, tableName, whereClause);
    }
}
