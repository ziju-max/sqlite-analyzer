package org.csu.sqliteanalyzer.ast.create;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQL数据类型，支持VARCHAR(30)这类长度参数。
 */
public class DataType {
    private final String name;
    private final List<Integer> parameters;

    public DataType(String name, List<Integer> parameters) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    }

    public String getName() {
        return name;
    }

    public List<Integer> getParameters() {
        return parameters;
    }

    public Optional<Integer> getLength() {
        if (parameters.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parameters.get(0));
    }

    public String toSQL() {
        if (parameters.isEmpty()) {
            return name;
        }

        return name + "(" + String.join(", ", parameters.stream()
                .map(String::valueOf)
                .toList()) + ")";
    }

    public String toTreeString(String childIndent) {
        if (parameters.isEmpty()) {
            return name;
        }

        StringBuilder tree = new StringBuilder(name);
        if (parameters.size() == 1) {
            tree.append("\n").append(childIndent).append("└── length: ").append(parameters.get(0));
        } else {
            tree.append("\n").append(childIndent).append("└── parameters: ").append(parameters);
        }
        return tree.toString();
    }

    @Override
    public String toString() {
        return "DataType{" +
                "name='" + name + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
