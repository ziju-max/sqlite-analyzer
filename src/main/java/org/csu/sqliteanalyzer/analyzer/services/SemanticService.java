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
    private final Map<String, Map<String, ValueType>> columnTypes;
    private final LexerService lexerService;

    public Map<String, Set<String>> getTables(){
        return tables;
    }

    public SemanticService() {
        this.tables = new LinkedHashMap<>();
        this.columnTypes = new LinkedHashMap<>();
        this.lexerService = new LexerService();
    }

    public SemanticService(Map<String, ?> tables) {
        this();
        try {
            Objects.requireNonNull(tables, "tables must not be null")
                    .forEach(this::registerTableDefinition);
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

            Map<String, ValueType> types = new LinkedHashMap<>();
            for (String columnName : columnNames) {
                types.put(columnName, ValueType.UNKNOWN);
            }
            registerTableInternal(tableName, columnNames, types);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid table definition: " + exceptionMessage(e), e);
        }
    }

    /**
     * 注册带类型的表结构。类型值可以是 INT/VARCHAR 字符串，也可以是项目中的
     * 存储层 DataType 枚举或 AST DataType 对象。
     */
    public void registerTable(String tableName, Map<String, ?> columns) {
        try {
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("table name must not be blank");
            }

            Map<String, ValueType> types = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry :
                    Objects.requireNonNull(columns, "columns must not be null").entrySet()) {
                String columnName = entry.getKey();
                if (columnName == null || columnName.isBlank()) {
                    throw new IllegalArgumentException("column name must not be blank");
                }
                types.put(normalize(columnName), parseType(entry.getValue()));
            }

            registerTableInternal(tableName, types.keySet(), types);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid table definition: " + exceptionMessage(e), e);
        }
    }

    private void registerTableDefinition(String tableName, Object definition) {
        if (definition instanceof Map<?, ?> typedColumns) {
            Map<String, Object> columns = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : typedColumns.entrySet()) {
                if (!(entry.getKey() instanceof String columnName)) {
                    throw new IllegalArgumentException("column name must be a string");
                }
                columns.put(columnName, entry.getValue());
            }
            registerTable(tableName, columns);
            return;
        }

        if (definition instanceof Collection<?> columnCollection) {
            List<String> columns = new ArrayList<>();
            for (Object column : columnCollection) {
                if (!(column instanceof String columnName)) {
                    throw new IllegalArgumentException("column name must be a string");
                }
                columns.add(columnName);
            }
            registerTable(tableName, columns);
            return;
        }

        throw new IllegalArgumentException(
                "table definition must be a collection of column names or a column type map");
    }

    private void registerTableInternal(
            String tableName,
            Collection<String> columns,
            Map<String, ValueType> types
    ) {
        String normalizedTableName = normalize(tableName);
        Set<String> columnNames = new LinkedHashSet<>();
        for (String column : columns) {
            columnNames.add(normalize(column));
        }

        Map<String, ValueType> normalizedTypes = new LinkedHashMap<>();
        for (String column : columnNames) {
            normalizedTypes.put(
                    column,
                    types.getOrDefault(column, ValueType.UNKNOWN)
            );
        }

        tables.put(normalizedTableName, columnNames);
        columnTypes.put(normalizedTableName, normalizedTypes);
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
                statement.getGroupByClause().orElse(null), columns,statement.getSelectList());

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

        String actualColumnName = resolveColumnName(tableName, columnName);
        if (columns != null && !columns.contains(normalize(actualColumnName))) {
            throw new SemanticException(
                    String.format("Column '%s' does not exist in table '%s'", columnName, tableName)
            );
        }
    }

    private String resolveColumnName(String tableName, String columnReference)
            throws SemanticException {
        String reference = columnReference.trim();
        int separator = reference.indexOf('.');
        if (separator < 0) {
            return reference;
        }

        if (separator == 0
                || separator == reference.length() - 1
                || separator != reference.lastIndexOf('.')) {
            throw new SemanticException(
                    String.format("Invalid qualified column name '%s'", columnReference)
            );
        }

        String qualifier = reference.substring(0, separator);
        if (!normalize(qualifier).equals(normalize(tableName))) {
            throw new SemanticException(
                    String.format("Column '%s' does not belong to table '%s'",
                            columnReference, tableName)
            );
        }

        return reference.substring(separator + 1);
    }

    private void checkWhereClause(String tableName, String whereClause, Set<String> columns)
            throws SemanticException {
        if (whereClause == null || whereClause.isBlank()) {
            return;
        }

        try {
            List<Map<String, Object>> tokens = lexerService.tokenize(whereClause);
            checkArithmeticTypes(tableName, tokens, columns);
            for (int i = 0; i < tokens.size(); i++) {
                Map<String, Object> token = tokens.get(i);
                if (!"identifier".equals(token.get("type"))) {
                    continue;
                }

                String value = (String) token.get("value");
                if (isWhereKeyword(value)) {
                    continue;
                }

                if (isQualifiedIdentifier(tokens, i)) {
                    String qualifiedColumn = value + "."
                            + tokens.get(i + 2).get("value");
                    checkColumn(tableName, qualifiedColumn, columns);
                    i += 2;
                    continue;
                }

                Map<String, Object> previousToken = i == 0 ? null : tokens.get(i - 1);
                if (columns != null
                        && (isConditionStart(previousToken)
                        || columns.contains(normalize(value)))) {
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

    /**
     * 检查 WHERE 中算术运算符两侧的操作数类型。
     *
     * <p>WHERE 当前仍以字符串形式保存在 AST 中，故这里直接基于词法 token
     * 推导算术子表达式类型。这样不会改变现有 AST/执行计划接口，同时能在执行
     * 计划生成前报告诸如 age + 'abc' 这样的语义错误。</p>
     */
    private void checkArithmeticTypes(
            String tableName,
            List<Map<String, Object>> tokens,
            Set<String> columns
    ) throws SemanticException {
        for (int i = 0; i < tokens.size(); i++) {
            if (!isBinaryArithmeticOperator(tokens, i)) {
                continue;
            }

            ValueType leftType = typeBeforeArithmeticOperator(
                    tableName, tokens, columns, i);
            ValueType rightType = typeAfterArithmeticOperator(
                    tableName, tokens, columns, i);
            rejectStringArithmetic(tokens.get(i), leftType, rightType);
        }
    }

    private void rejectStringArithmetic(
            Map<String, Object> operatorToken,
            ValueType leftType,
            ValueType rightType
    ) throws SemanticException {
        if (leftType == ValueType.UNKNOWN
                || rightType == ValueType.UNKNOWN
                || leftType == ValueType.NULL
                || rightType == ValueType.NULL) {
            return;
        }
        if (leftType != ValueType.STRING && rightType != ValueType.STRING) {
            return;
        }

        throw new SemanticException(
                String.format(
                        "类型不匹配：算术运算符 '%s' 不能作用于 %s 和 %s",
                        operatorToken.get("value"),
                        displayType(leftType),
                        displayType(rightType)
                )
        );
    }

    private ValueType typeBeforeArithmeticOperator(
            String tableName,
            List<Map<String, Object>> tokens,
            Set<String> columns,
            int operatorIndex
    ) throws SemanticException {
        int operandEnd = operatorIndex - 1;
        if (operandEnd < 0) {
            return ValueType.UNKNOWN;
        }

        if (")".equals(tokens.get(operandEnd).get("value"))) {
            int openingIndex = findOpeningParenthesis(tokens, operandEnd);
            return openingIndex < 0
                    ? ValueType.UNKNOWN
                    : inferArithmeticType(
                    tableName, tokens, columns, openingIndex + 1, operandEnd - 1);
        }

        int start = qualifiedIdentifierStart(tokens, operandEnd);
        if (start > 0 && isUnarySign(tokens, start - 1)) {
            start--;
        }
        return inferArithmeticType(tableName, tokens, columns, start, operandEnd);
    }

    private ValueType typeAfterArithmeticOperator(
            String tableName,
            List<Map<String, Object>> tokens,
            Set<String> columns,
            int operatorIndex
    ) throws SemanticException {
        int operandStart = operatorIndex + 1;
        if (operandStart >= tokens.size()) {
            return ValueType.UNKNOWN;
        }

        if ("(".equals(tokens.get(operandStart).get("value"))) {
            int closingIndex = findClosingParenthesis(tokens, operandStart);
            return closingIndex < 0
                    ? ValueType.UNKNOWN
                    : inferArithmeticType(
                    tableName, tokens, columns, operandStart + 1, closingIndex - 1);
        }

        int end = isUnarySign(tokens, operandStart)
                ? qualifiedIdentifierEnd(tokens, operandStart + 1)
                : qualifiedIdentifierEnd(tokens, operandStart);
        return inferArithmeticType(tableName, tokens, columns, operandStart, end);
    }

    private ValueType inferArithmeticType(
            String tableName,
            List<Map<String, Object>> tokens,
            Set<String> columns,
            int start,
            int end
    ) throws SemanticException {
        while (start <= end
                && "(".equals(tokens.get(start).get("value"))
                && findClosingParenthesis(tokens, start) == end) {
            start++;
            end--;
        }
        if (start > end) {
            return ValueType.UNKNOWN;
        }

        int depth = 0;
        for (int i = start; i <= end; i++) {
            String value = String.valueOf(tokens.get(i).get("value"));
            if ("(".equals(value)) {
                depth++;
            } else if (")".equals(value)) {
                depth--;
            } else if (depth == 0 && isBinaryArithmeticOperator(tokens, i)) {
                ValueType leftType = inferArithmeticType(
                        tableName, tokens, columns, start, i - 1);
                ValueType rightType = inferArithmeticType(
                        tableName, tokens, columns, i + 1, end);
                rejectStringArithmetic(tokens.get(i), leftType, rightType);
                return mergeArithmeticTypes(leftType, rightType);
            }
        }

        if (isUnarySign(tokens, start)) {
            return inferArithmeticType(tableName, tokens, columns, start + 1, end);
        }

        if (start == end) {
            return typeOfToken(tableName, tokens, columns, start);
        }

        if (end - start == 2
                && ".".equals(tokens.get(start + 1).get("value"))) {
            return typeOfColumn(
                    tableName,
                    String.valueOf(tokens.get(start).get("value"))
                            + "." + tokens.get(end).get("value"),
                    columns
            );
        }

        return ValueType.UNKNOWN;
    }

    private ValueType mergeArithmeticTypes(ValueType leftType, ValueType rightType) {
        if (leftType == ValueType.UNKNOWN || rightType == ValueType.UNKNOWN) {
            return ValueType.UNKNOWN;
        }
        if (leftType == ValueType.NULL || rightType == ValueType.NULL) {
            return ValueType.UNKNOWN;
        }
        if (leftType == ValueType.STRING || rightType == ValueType.STRING) {
            return ValueType.STRING;
        }
        if (leftType == ValueType.INT || rightType == ValueType.INT) {
            return ValueType.INT;
        }
        return ValueType.UNKNOWN;
    }

    private ValueType typeOfToken(
            String tableName,
            List<Map<String, Object>> tokens,
            Set<String> columns,
            int index
    ) throws SemanticException {
        Map<String, Object> token = tokens.get(index);
        String tokenType = String.valueOf(token.get("type"));
        String value = String.valueOf(token.get("value"));

        if ("number".equals(tokenType)) {
            return ValueType.INT;
        }
        if ("string".equals(tokenType) || isQuotedString(value)) {
            return ValueType.STRING;
        }
        if ("keyword".equals(tokenType) && "NULL".equalsIgnoreCase(value)) {
            return ValueType.NULL;
        }
        if ("identifier".equals(tokenType)) {
            return typeOfColumn(tableName, value, columns);
        }
        return ValueType.UNKNOWN;
    }

    private ValueType typeOfColumn(
            String tableName,
            String columnReference,
            Set<String> columns
    ) throws SemanticException {
        if (columns == null) {
            return ValueType.UNKNOWN;
        }

        String columnName = resolveColumnName(tableName, columnReference);
        if (!columns.contains(normalize(columnName))) {
            return ValueType.UNKNOWN;
        }

        Map<String, ValueType> types = columnTypes.get(normalize(tableName));
        return types == null
                ? ValueType.UNKNOWN
                : types.getOrDefault(normalize(columnName), ValueType.UNKNOWN);
    }

    private boolean isBinaryArithmeticOperator(
            List<Map<String, Object>> tokens,
            int index
    ) {
        String value = String.valueOf(tokens.get(index).get("value"));
        if (!Set.of("+", "-", "*", "/").contains(value)) {
            return false;
        }
        if ("*".equals(value)) {
            return true;
        }
        if (index == 0) {
            return false;
        }

        Map<String, Object> previous = tokens.get(index - 1);
        String previousValue = String.valueOf(previous.get("value"));
        return !Set.of(
                "=", "==", "!=", "<", "<=", ">", ">="
        ).contains(value);
    }

    private boolean isUnarySign(List<Map<String, Object>> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return false;
        }
        String value = String.valueOf(tokens.get(index).get("value"));
        return ("+".equals(value) || "-".equals(value))
                && !isBinaryArithmeticOperator(tokens, index);
    }

    private int qualifiedIdentifierStart(List<Map<String, Object>> tokens, int end) {
        if (end >= 2
                && ".".equals(tokens.get(end - 1).get("value"))
                && "identifier".equals(tokens.get(end - 2).get("type"))) {
            return end - 2;
        }
        return end;
    }

    private int qualifiedIdentifierEnd(List<Map<String, Object>> tokens, int start) {
        if (start + 2 < tokens.size()
                && ".".equals(tokens.get(start + 1).get("value"))
                && "identifier".equals(tokens.get(start + 2).get("type"))) {
            return start + 2;
        }
        return start;
    }

    private int findClosingParenthesis(List<Map<String, Object>> tokens, int openingIndex) {
        int depth = 0;
        for (int i = openingIndex; i < tokens.size(); i++) {
            String value = String.valueOf(tokens.get(i).get("value"));
            if ("(".equals(value)) {
                depth++;
            } else if (")".equals(value) && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private int findOpeningParenthesis(List<Map<String, Object>> tokens, int closingIndex) {
        int depth = 0;
        for (int i = closingIndex; i >= 0; i--) {
            String value = String.valueOf(tokens.get(i).get("value"));
            if (")".equals(value)) {
                depth++;
            } else if ("(".equals(value) && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isQuotedString(String value) {
        return value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")));
    }

    private ValueType parseType(Object type) {
        if (type instanceof ValueType valueType) {
            return valueType;
        }
        if (type instanceof org.csu.sqliteanalyzer.engine.metadata.DataType dataType) {
            return dataType == org.csu.sqliteanalyzer.engine.metadata.DataType.INT
                    ? ValueType.INT
                    : ValueType.STRING;
        }
        if (type instanceof org.csu.sqliteanalyzer.analyzer.ast.create.DataType dataType) {
            return parseTypeName(dataType.getName());
        }
        if (type instanceof String typeName) {
            return parseTypeName(typeName);
        }
        throw new IllegalArgumentException(
                "unsupported column type '" + type + "'");
    }

    private ValueType parseTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("column type must not be blank");
        }

        String upperTypeName = typeName.toUpperCase(Locale.ROOT);
        if (upperTypeName.contains("INT")) {
            return ValueType.INT;
        }
        return ValueType.STRING;
    }

    private String displayType(ValueType type) {
        return switch (type) {
            case INT -> "INT";
            case STRING -> "字符串";
            case NULL -> "NULL";
            case UNKNOWN -> "未知类型";
        };
    }

    private void checkGroupByClause(
            String tableName,
            String groupByClause,
            Set<String> columns,
            List<Object> selectList
    )
            throws SemanticException {
        if (groupByClause == null || groupByClause.isBlank() || columns == null) {
            return;
        }

        for (String groupColumn : groupByClause.split(",")) {
            checkColumn(tableName, groupColumn.trim(), columns);
        }
        List<String> groupColumns = new ArrayList<>();
        for (String groupColumn : groupByClause.split(",")) {
            groupColumns.add(normalize(resolveColumnName(tableName, groupColumn.trim())));
        }
        for (Object item : selectList) {
            if (item instanceof String selectName && !"*".equals(selectName)) {
                String normalizedSelectName =
                        normalize(resolveColumnName(tableName, selectName));
                if (!groupColumns.contains(normalizedSelectName)) {
                    throw new SemanticException(
                            String.format("SELECT item %s is not in GROUP BY Clause", selectName)
                    );
                }
            }
        }
    }

    private boolean isQualifiedIdentifier(List<Map<String, Object>> tokens, int index) {
        if (index + 2 >= tokens.size()) {
            return false;
        }

        Map<String, Object> separator = tokens.get(index + 1);
        Map<String, Object> column = tokens.get(index + 2);
        return ".".equals(separator.get("value"))
                && "identifier".equals(column.get("type"));
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

    private enum ValueType {
        INT,
        STRING,
        NULL,
        UNKNOWN
    }
}
