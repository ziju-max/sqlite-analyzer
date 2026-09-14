package org.csu.sqliteanalyzer.analyzer.ast.create;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;

import java.util.List;
import java.util.Objects;

/**
 * CREATE TABLE语句的AST节点。
 */
public class CreateTableStatement extends ASTNode {
    private final String tableName;
    private final List<ColumnDefinition> columnDefinitions;

    public CreateTableStatement(String tableName, List<ColumnDefinition> columnDefinitions) {
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.columnDefinitions = List.copyOf(Objects.requireNonNull(columnDefinitions, "columnDefinitions must not be null"));
    }

    public String getTableName() {
        return tableName;
    }

    public List<ColumnDefinition> getColumnDefinitions() {
        return columnDefinitions;
    }

    public List<String> getColumns() {
        return columnDefinitions.stream()
                .map(ColumnDefinition::toSQL)
                .toList();
    }

    @Override
    public String getNodeType() {
        return "CREATE";
    }

    @Override
    public String toSQL() {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");

        for (int i = 0; i < columnDefinitions.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columnDefinitions.get(i).toSQL());
        }

        return sql.append(")").toString();
    }

    @Override
    public String toString() {
        StringBuilder tree = new StringBuilder("CreateTableStatement (CREATE TABLE)\n");
        tree.append("├── tableName: ").append(tableName).append("\n");
        tree.append("└── columns");

        for (int i = 0; i < columnDefinitions.size(); i++) {
            boolean last = i == columnDefinitions.size() - 1;
            tree.append("\n")
                    .append(last ? "    └── " : "    ├── ")
                    .append(columnDefinitions.get(i).toTreeString(last ? "        " : "    │   "));
        }

        return tree.toString();
    }

}
