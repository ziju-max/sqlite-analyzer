package org.csu.sqliteanalyzer.ast.create;

/**
 * CREATE TABLE列约束。
 */
public interface ColumnConstraint {
    String toSQL();
}
