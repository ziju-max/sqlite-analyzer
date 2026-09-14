package org.csu.sqliteanalyzer.analyzer.ast.select;

import java.util.Locale;
import java.util.Optional;

/**
 * SELECT 语句中的 JOIN 子句。
 */
public class JoinClause {
    private final String joinType;
    private final String tableName;
    private final String condition;

    public JoinClause(String tableName, String condition) {
        this("INNER", tableName, condition);
    }

    public JoinClause(String joinType, String tableName, String condition) {
        this.joinType = joinType.toUpperCase(Locale.ROOT);
        this.tableName = tableName;
        this.condition = condition;
    }

    public String getJoinType() {
        return joinType;
    }

    public String getTableName() {
        return tableName;
    }

    public Optional<String> getCondition() {
        return Optional.ofNullable(condition);
    }

    public boolean hasCondition() {
        return condition != null && !condition.isBlank();
    }

    public String toSQL() {
        StringBuilder sql = new StringBuilder();
        if (!"INNER".equals(joinType)) {
            sql.append(joinType).append(" ");
        }
        sql.append("JOIN ").append(tableName);
        if (hasCondition()) {
            sql.append(" ON ").append(condition);
        }
        return sql.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "Join{joinType='%s', tableName='%s', condition=%s}",
                joinType, tableName, condition);
    }
}
