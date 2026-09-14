package org.csu.sqliteanalyzer.ast.create;

/**
 * AUTOINCREMENT列约束。
 */
public class AutoIncrementConstraint implements ColumnConstraint {
    @Override
    public String toSQL() {
        return "AUTOINCREMENT";
    }

    @Override
    public String toString() {
        return "AutoIncrementConstraint";
    }
}
