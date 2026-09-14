package org.csu.sqliteanalyzer.ast.create;

/**
 * PRIMARY KEY列约束。
 */
public class PrimaryKeyConstraint implements ColumnConstraint {
    @Override
    public String toSQL() {
        return "PRIMARY KEY";
    }

    @Override
    public String toString() {
        return "PrimaryKeyConstraint";
    }
}
