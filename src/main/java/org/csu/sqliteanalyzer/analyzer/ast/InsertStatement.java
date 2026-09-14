package org.csu.sqliteanalyzer.analyzer.ast;

import java.util.List;

/**
 * INSERT语句的AST节点
 * 对应文法: INSERT INTO table_name [ LPAREN column_list RPAREN ] VALUES LPAREN value_list RPAREN
 */
public class InsertStatement extends ASTNode {
    private String tableName;
    private List<String> columns;
    private List<String> values;

    public InsertStatement(String tableName, List<String> columns, List<String> values) {
        this.tableName = tableName;
        this.columns = columns;
        this.values = values;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public String getNodeType() {
        return "INSERT";
    }

    @Override
    public String toSQL() {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName);

        if (!columns.isEmpty()) {
            sql.append(" (");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns.get(i));
            }
            sql.append(")");
        }

        sql.append(" VALUES (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(values.get(i));
        }
        sql.append(")");

        return sql.toString();
    }

    @Override
    public String toString() {
        return String.format("InsertStatement{tableName='%s', columns=%s, values=%s}",
                tableName, columns, values);
    }
}
