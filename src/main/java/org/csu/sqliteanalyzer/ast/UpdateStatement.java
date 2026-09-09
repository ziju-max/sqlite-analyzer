package org.csu.sqliteanalyzer.ast;

import java.util.List;
import java.util.Optional;
import org.csu.sqliteanalyzer.common.Assignment;

/**
 * UPDATE语句的AST节点
 * 对应文法: UPDATE table_name SET assignment_list [ where_clause ]
 */
public class UpdateStatement extends ASTNode {
    private String tableName;
    private List<Assignment> assignments;
    private String whereClause;

    public UpdateStatement(String tableName, List<Assignment> assignments, String whereClause) {
        this.tableName = tableName;
        this.assignments = assignments;
        this.whereClause = whereClause;
    }

    public String getTableName() {
        return tableName;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public Optional<String> getWhereClause() {
        return Optional.ofNullable(whereClause);
    }

    @Override
    public String getNodeType() {
        return "UPDATE";
    }

    @Override
    public String toSQL() {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(tableName).append(" SET ");

        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(assignments.get(i));
        }

        if (whereClause != null) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    @Override
    public String toString() {
        return String.format("UpdateStatement{tableName='%s', assignments=%s, whereClause=%s}",
                tableName, assignments, whereClause);
    }
}
