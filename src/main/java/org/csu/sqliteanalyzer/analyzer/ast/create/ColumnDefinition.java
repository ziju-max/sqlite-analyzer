package org.csu.sqliteanalyzer.analyzer.ast.create;

import java.util.List;
import java.util.Objects;

/**
 * CREATE TABLE中的列定义。
 */
public class ColumnDefinition {
    private final String name;
    private final DataType dataType;
    private final List<ColumnConstraint> constraints;

    public ColumnDefinition(String name, DataType dataType, List<ColumnConstraint> constraints) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.dataType = Objects.requireNonNull(dataType, "dataType must not be null");
        this.constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints must not be null"));
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public List<ColumnConstraint> getConstraints() {
        return constraints;
    }

    public String toSQL() {
        StringBuilder sql = new StringBuilder(name).append(' ').append(dataType.toSQL());
        for (ColumnConstraint constraint : constraints) {
            sql.append(' ').append(constraint.toSQL());
        }
        return sql.toString();
    }

    public String toTreeString(String childIndent) {
        StringBuilder tree = new StringBuilder("ColumnDefinition\n");
        tree.append(childIndent).append("├── name: ").append(name).append("\n");
        tree.append(childIndent).append("├── dataType: ").append(dataType.toTreeString(childIndent + "│   ")).append("\n");
        tree.append(childIndent).append("└── constraints");

        if (constraints.isEmpty()) {
            tree.append(": []");
            return tree.toString();
        }

        for (ColumnConstraint constraint : constraints) {
            tree.append("\n").append(childIndent).append("    └── ").append(constraint);
        }
        return tree.toString();
    }

    @Override
    public String toString() {
        return "ColumnDefinition{" +
                "name='" + name + '\'' +
                ", dataType=" + dataType +
                ", constraints=" + constraints +
                '}';
    }
}
