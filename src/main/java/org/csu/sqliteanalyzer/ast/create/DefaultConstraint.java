package org.csu.sqliteanalyzer.ast.create;

import java.util.Objects;

/**
 * DEFAULT列约束。
 */
public class DefaultConstraint implements ColumnConstraint {
    private final String value;

    public DefaultConstraint(String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toSQL() {
        return "DEFAULT " + value;
    }

    @Override
    public String toString() {
        return "DefaultConstraint(" + value + ")";
    }
}
