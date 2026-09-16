package org.csu.sqliteanalyzer.analyzer.ast;

import java.util.Optional;

/**
 * DELETE语句的AST节点
 * 对应文法: DELETE FROM table_name [ where_clause ]
 */
public class DeleteStatement extends ASTNode {
    private String tableName;
    private String whereClause;

    public DeleteStatement(String tableName, String whereClause) {
        this.tableName = tableName;
        this.whereClause = whereClause;
    }

    public String getTableName() {
        return tableName;
    }

    public Optional<String> getWhereClause() {
        return Optional.ofNullable(whereClause);
    }

    @Override
    public String getNodeType() {
        return "DELETE";
    }

    @Override
    public String toSQL() {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(tableName);

        if (whereClause != null) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    @Override
    public String toString() {
        return renderTree(
                "DeleteStatement (DELETE)",
                treeProperty("tableName", tableName),
                whereClauseTree(whereClause)
        );
    }
}
