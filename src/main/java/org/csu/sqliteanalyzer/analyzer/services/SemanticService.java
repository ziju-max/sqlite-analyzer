package org.csu.sqliteanalyzer.analyzer.services;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.DeleteStatement;
import org.csu.sqliteanalyzer.analyzer.ast.InsertStatement;
import org.csu.sqliteanalyzer.analyzer.ast.UpdateStatement;
import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.analyzer.common.Assignment;
import org.csu.sqliteanalyzer.analyzer.common.SelectItem;
import org.csu.sqliteanalyzer.analyzer.exception.LexerException;
import org.csu.sqliteanalyzer.analyzer.exception.SemanticException;

import java.util.*;

/**
 * SQL语义分析服务
 * 负责检查AST中的表、列以及DML语句之间的语义关系
 */
public class SemanticService {
    private final Map<String, Set<String>> tables;
    private final LexerService lexerService;

    public Map<String, Set<String>> getTables(){
        return tables;
    }

    public SemanticService() {
        this.tables = new LinkedHashMap<>();
        this.lexerService = new LexerService();
    }

    public SemanticService(Map<String, ? extends Collection<String>> tables) {
        this();
        try {
            Objects.requireNonNull(tables, "tables must not be null")
                    .forEach(this::registerTable);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid table definitions: " + exceptionMessage(e), e);
        }
    }

    public void registerTable(String tableName, Collection<String> columns) {
        try {
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("table name must not be blank");
            }

            Set<String> columnNames = new LinkedHashSet<>();
            for (String column : Objects.requireNonNull(columns, "columns must not be null")) {
                if (column == null || column.isBlank()) {
                    throw new IllegalArgumentException("column name must not be blank");
                }
                columnNames.add(normalize(column));
            }

            tables.put(normalize(tableName), columnNames);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid table definition: " + exceptionMessage(e), e);
        }
    }

    public void registerTable(String tableName, String... columns) {
        registerTable(tableName, Arrays.asList(columns));
    }

    public ASTNode analyze(ASTNode ast) throws SemanticException {
        try {
            return analyzeInternal(ast);
        } catch (SemanticException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SemanticException(
                    "Unable to analyze SQL semantics: " + exceptionMessage(e), e);
        }
    }

    private ASTNode analyzeInternal(ASTNode ast) throws SemanticException {
        if (ast == null) {
            throw new SemanticException("AST node must not be null");
        }

        if (ast instanceof SelectStatement) {
            analyzeSelect((SelectStatement) ast);
        } else if (ast instanceof InsertStatement) {
            analyzeInsert((InsertStatement) ast);
        } else if (ast instanceof UpdateStatement) {
            analyzeUpdate((UpdateStatement) ast);
        } else if (ast instanceof DeleteStatement) {
            analyzeDelete((DeleteStatement) ast);
        } else {
            throw new SemanticException(
                    String.format("Unsupported AST node type '%s'", ast.getNodeType())
            );
        }

        return ast;
    }

    public boolean isValid(ASTNode ast) {
        try {
            analyze(ast);
            return true;
        } catch (SemanticException | RuntimeException e) {
            return false;
        }
    }

    private void analyzeSelect(SelectStatement statement) throws SemanticException {
        Set<String> columns = findTableColumns(statement.getTableName());

        if (statement.getSelectList() == null || statement.getSelectList().isEmpty()) {
            throw new SemanticException("SELECT list must not be empty");
        }

        for (Object item : statement.getSelectList()) {
            if (item instanceof String columnName) {
                if (!"*".equals(columnName)) {
                    checkColumn(statement.getTableName(), columnName, columns);
                }
            } else if (item instanceof SelectItem) {
                checkColumn(statement.getTableName(),
                        ((SelectItem) item).getColumnName(), columns);
            } else if (item instanceof AggregateFunction aggregateFunction) {
                if (!"*".equals(aggregateFunction.getIdentifier())) {
                    checkColumn(statement.getTableName(),
                            aggregateFunction.getIdentifier(), columns);
                }
            } else {
                throw new SemanticException(String.format("Unknown %s",item));
            }
        }

        checkWhereClause(statement.getTableName(), statement.getWhereClause().orElse(null), columns);
        checkGroupByClause(statement.getTableName(),
                statement.getGroupByClause().orElse(null), columns);
    }

    private void analyzeInsert(InsertStatement statement) throws SemanticException {
        Set<String> columns = findTableColumns(statement.getTableName());
        List<String> insertColumns = statement.getColumns();
        List<String> values = statement.getValues();

        if (values == null || values.isEmpty()) {
            throw new SemanticException("INSERT values must not be empty");
        }

        if (insertColumns == null || insertColumns.isEmpty()) {
            if (columns != null && values.size() != columns.size()) {
                throw new SemanticException(
                        String.format("INSERT value count %d does not match column count %d",
                                values.size(), columns.size())
                );
            }
            return;
        }

        Set<String> usedColumns = new HashSet<>();
        for (String column : insertColumns) {
            checkColumn(statement.getTableName(), column, columns);
            if (!usedColumns.add(normalize(column))) {
                throw new SemanticException(
                        String.format("Duplicate INSERT column '%s'", column)
                );
            }
        }

        if (insertColumns.size() != values.size()) {
            throw new SemanticException(
                    String.format("INSERT column count %d does not match value count %d",
                            insertColumns.size(), values.size())
            );
        }
    }

    private void analyzeUpdate(UpdateStatement statement) throws SemanticException {
        Set<String> columns = findTableColumns(statement.getTableName());
        List<Assignment> assignments = statement.getAssignments();

        if (assignments == null || assignments.isEmpty()) {
            throw new SemanticException("UPDATE assignment list must not be empty");
        }

        Set<String> usedColumns = new HashSet<>();
        for (Assignment assignment : assignments) {
            checkColumn(statement.getTableName(), assignment.getColumnName(), columns);
            if (!usedColumns.add(normalize(assignment.getColumnName()))) {
                throw new SemanticException(
                        String.format("Duplicate UPDATE column '%s'", assignment.getColumnName())
                );
            }
        }

        checkWhereClause(statement.getTableName(), statement.getWhereClause().orElse(null), columns);
    }

    private void analyzeDelete(DeleteStatement statement) throws SemanticException {
        Set<String> columns = findTableColumns(statement.getTableName());
        checkWhereClause(statement.getTableName(), statement.getWhereClause().orElse(null), columns);
    }

    private Set<String> findTableColumns(String tableName) throws SemanticException {
        if (tableName == null || tableName.isBlank()) {
            throw new SemanticException("Table name must not be blank");
        }

        if (tables.isEmpty()) {
            return null;
        }

        Set<String> columns = tables.get(normalize(tableName));
        if (columns == null) {
            throw new SemanticException(
                    String.format("Table '%s' does not exist", tableName)
            );
        }
        return columns;
    }

    private void checkColumn(String tableName, String columnName, Set<String> columns)
            throws SemanticException {
        if (columnName == null || columnName.isBlank()) {
            throw new SemanticException("Column name must not be blank");
        }

        if (columns != null && !columns.contains(normalize(columnName))) {
            throw new SemanticException(
                    String.format("Column '%s' does not exist in table '%s'", columnName, tableName)
            );
        }
    }

    private void checkWhereClause(String tableName, String whereClause, Set<String> columns)
            throws SemanticException {
        if (whereClause == null || whereClause.isBlank() || columns == null) {
            return;
        }

        try {
            List<Map<String, Object>> tokens = lexerService.tokenize(whereClause);
            for (int i = 0; i < tokens.size(); i++) {
                Map<String, Object> token = tokens.get(i);
                if (!"identifier".equals(token.get("type"))) {
                    continue;
                }

                String value = (String) token.get("value");
                if (isWhereKeyword(value)) {
                    continue;
                }

                Map<String, Object> previousToken = i == 0 ? null : tokens.get(i - 1);
                if (isConditionStart(previousToken)
                        || columns.contains(normalize(value))) {
                    checkColumn(tableName, value, columns);
                }
            }
        } catch (LexerException e) {
            throw new SemanticException("Invalid WHERE clause: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SemanticException(
                    "Unable to validate WHERE clause: " + exceptionMessage(e), e);
        }
    }

    private void checkGroupByClause(String tableName, String groupByClause, Set<String> columns)
            throws SemanticException {
        if (groupByClause == null || groupByClause.isBlank() || columns == null) {
            return;
        }

        for (String groupColumn : groupByClause.split(",")) {
            checkColumn(tableName, groupColumn.trim(), columns);
        }
    }

    private boolean isConditionStart(Map<String, Object> previousToken) {
        if (previousToken == null) {
            return true;
        }

        String value = (String) previousToken.get("value");
        return "AND".equalsIgnoreCase(value)
                || "OR".equalsIgnoreCase(value)
                || "NOT".equalsIgnoreCase(value)
                || "(".equals(value)
                || ",".equals(value);
    }

    private boolean isWhereKeyword(String value) {
        return Set.of("AND", "OR", "NOT", "IS", "NULL", "LIKE", "IN", "BETWEEN", "ESCAPE")
                .contains(value.toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String exceptionMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
