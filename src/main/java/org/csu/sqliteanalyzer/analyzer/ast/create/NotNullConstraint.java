package org.csu.sqliteanalyzer.analyzer.ast.create;

/**
 * NOT NULL列约束。
 */
public class NotNullConstraint implements ColumnConstraint {
    @Override
    public String toSQL() {
        return "NOT NULL";
    }

    @Override
    public String toString() {
        return "NotNullConstraint";
    }
}
