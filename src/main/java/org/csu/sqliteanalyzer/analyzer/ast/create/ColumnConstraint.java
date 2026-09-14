package org.csu.sqliteanalyzer.analyzer.ast.create;

/**
 * CREATE TABLE列约束。
 */
public interface ColumnConstraint {
    String toSQL();
}
