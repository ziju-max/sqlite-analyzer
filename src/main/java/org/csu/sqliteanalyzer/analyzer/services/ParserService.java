package org.csu.sqliteanalyzer.analyzer.services;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.DeleteStatement;
import org.csu.sqliteanalyzer.analyzer.ast.InsertStatement;
import org.csu.sqliteanalyzer.analyzer.ast.UpdateStatement;
import org.csu.sqliteanalyzer.analyzer.ast.create.*;
import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.ast.select.JoinClause;
import org.csu.sqliteanalyzer.analyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.analyzer.common.Assignment;
import org.csu.sqliteanalyzer.analyzer.exception.SyntaxException;

import java.util.*;

public class ParserService {
    private static final Set<String> COLUMN_CONSTRAINTS = Set.of(
            "PRIMARY KEY",
            "AUTO_INCREMENT",
            "NOT NULL",
            "DEFAULT"
    );
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
            "COUNT", "SUM", "AVG", "MAX", "MIN"
    );

    private List<Map<String,Object>> tokens;
    private int pos;
    private Map<String,Object> currentToken;

    public ParserService(List<Map<String,Object>> tokens) {
        try {
            this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens must not be null"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid parser token list", e);
        }
        this.pos = 0;
        this.currentToken = this.tokens.isEmpty() ? null : this.tokens.get(0);
    }

    public ASTNode parse() throws SyntaxException {
        try {
            return parseInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SyntaxException(
                    "Unable to parse SQL statement: " + exceptionMessage(e),
                    tokenLine(currentToken),
                    tokenColumn(currentToken),
                    e
            );
        }
    }

    private ASTNode parseInternal() throws SyntaxException {
        if (currentToken == null) {
            throw new SyntaxException("Empty SQL statement");
        }

        switch (currentValue().toUpperCase(Locale.ROOT)) {
            case "CREATE":
                return parseCreate();
            case "SELECT":
                return parseSelect();
            case "INSERT":
                return parseInsert();
            case "UPDATE":
                return parseUpdate();
            case "DELETE":
                return parseDelete();
            default:
                throw new SyntaxException(
                        String.format("Unexpected token '%s', expected CREATE, SELECT, INSERT, UPDATE, or DELETE",
                                currentToken.get("value")),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
        }
    }

    private void advance() {
        pos++;
        if (pos < tokens.size()) {
            currentToken = tokens.get(pos);
        } else {
            currentToken = null;
        }
    }
    private Map<String,Object> expect(String keyword) throws SyntaxException {
        if (currentToken == null || !String.valueOf(currentToken.get("value")).equalsIgnoreCase(keyword)) {
            throw new SyntaxException(
                    String.format("Expected %s, got %s", keyword,
                            currentToken == null ? "end of statement" : currentToken.get("value")),
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }
        Map<String,Object> token = currentToken;
        advance();
        return token;
    }
    public SelectStatement parseSelect() throws SyntaxException {
        try {
            return parseSelectInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw syntaxException("Unable to parse SELECT statement", e);
        }
    }

    private SelectStatement parseSelectInternal() throws SyntaxException {
        expect("SELECT");
        List<Object> selectList = parseSelectList();
        expect("FROM");
        String tableName = parseTableName();
        List<JoinClause> joinClauses = parseJoins();
        String whereClause = null;
        if (isCurrentKeyword("WHERE")) {
            whereClause = parseWhereClause();
        }
        String groupByClause = null;
        if (isCurrentKeyword("GROUP")) {
            groupByClause = parseGroupByClause();
        }
        String orderByClause = null;
        if (isCurrentKeyword("ORDER")) {
            orderByClause = parseOrderByClause();
        }
        return new SelectStatement(
                selectList, tableName, joinClauses, whereClause, groupByClause, orderByClause);
    }

    private List<JoinClause> parseJoins() throws SyntaxException {
        List<JoinClause> joinClauses = new ArrayList<>();

        while (isJoinStart()) {
            String joinType = parseJoinType();
            String tableName = parseTableName();
            String condition = null;

            if (isCurrentKeyword("ON")) {
                if ("CROSS".equals(joinType)) {
                    throw new SyntaxException("CROSS JOIN does not support an ON condition");
                }
                condition = parseJoinCondition();
            } else if (!"CROSS".equals(joinType)) {
                throw new SyntaxException(
                        "Expected ON after " + joinType + " JOIN " + tableName);
            }

            joinClauses.add(new JoinClause(joinType, tableName, condition));
        }

        return joinClauses;
    }

    private String parseJoinType() throws SyntaxException {
        if (isCurrentKeyword("JOIN")) {
            advance();
            return "INNER";
        }

        String joinType = currentKeyword();
        advance();
        if (isCurrentKeyword("OUTER")) {
            advance();
        }
        expect("JOIN");
        return joinType;
    }

    private String parseJoinCondition() throws SyntaxException {
        expect("ON");
        if (currentToken == null || isCurrentValue(";") || isJoinStart()
                || isCurrentKeyword("WHERE") || isCurrentKeyword("GROUP")
                || isCurrentKeyword("ORDER")) {
            throw new SyntaxException("Expected condition after ON");
        }

        List<String> values = new ArrayList<>();
        while (currentToken != null
                && !isCurrentValue(";")
                && !isJoinStart()
                && !isCurrentKeyword("WHERE")
                && !isCurrentKeyword("GROUP")
                && !isCurrentKeyword("ORDER")) {
            values.add(currentValue());
            advance();
        }
        return renderSqlFragment(values);
    }

    private boolean isJoinStart() {
        return isCurrentKeyword("JOIN")
                || isCurrentKeyword("INNER")
                || isCurrentKeyword("LEFT")
                || isCurrentKeyword("RIGHT")
                || isCurrentKeyword("FULL")
                || isCurrentKeyword("CROSS");
    }

    public CreateTableStatement parseCreate() throws SyntaxException {
        try {
            return parseCreateInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw syntaxException("Unable to parse CREATE TABLE statement", e);
        }
    }

    private CreateTableStatement parseCreateInternal() throws SyntaxException {
        expect("CREATE");
        expect("TABLE");
        if (isCurrentKeyword("IF")) {
            advance();
            expect("NOT");
            expect("EXISTS");
        }
        String tableName = parseTableName();
        expect("(");

        List<ColumnDefinition> columns = new ArrayList<>();
        while (!isCurrentValue(")")) {
            columns.add(parseColumnDefinition());

            if (isCurrentValue(",")) {
                advance();
                if (isCurrentValue(")")) {
                    throw new SyntaxException("Expected column definition after comma");
                }
            } else if (!isCurrentValue(")")) {
                throw new SyntaxException("Expected ',' or ')' after column definition");
            }
        }

        expect(")");
        return new CreateTableStatement(tableName, columns);
    }

    private ColumnDefinition parseColumnDefinition() throws SyntaxException {
        if (currentToken == null || !"identifier".equals(currentToken.get("type"))) {
            throw new SyntaxException("Expected column name");
        }

        String columnName = (String) currentToken.get("value");
        advance();

        DataType dataType = parseDataType(columnName);

        List<ColumnConstraint> constraints = new ArrayList<>();
        while (currentToken != null
                && !isCurrentValue(",")
                && !isCurrentValue(")")) {
            constraints.add(parseColumnConstraint());
        }

        return new ColumnDefinition(columnName, dataType, constraints);
    }

    private DataType parseDataType(String columnName) throws SyntaxException {
        if (currentToken == null || !"datatype".equals(currentToken.get("type"))) {
            throw new SyntaxException("Expected data type for column '" + columnName + "'");
        }

        String name = (String) currentToken.get("value");
        advance();

        List<Integer> parameters = List.of();
        if (isCurrentValue("(")) {
            parameters = parseTypeParameters();
        }

        return new DataType(name, parameters);
    }

    private ColumnConstraint parseColumnConstraint() throws SyntaxException {
        String keyword = currentKeyword();
        String constraint = switch (keyword) {
            case "PRIMARY" -> "PRIMARY KEY";
            case "AUTO_INCREMENT" -> "AUTO_INCREMENT";
            case "NOT" -> "NOT NULL";
            case "DEFAULT" -> "DEFAULT";
            default -> null;
        };

        if (constraint == null || !COLUMN_CONSTRAINTS.contains(constraint)) {
            throw new SyntaxException(
                    "Unsupported column constraint '" + currentValue() + "'"
            );
        }

        switch (constraint) {
            case "PRIMARY KEY":
                advance();
                consumeConstraintKeyword("KEY");
                return new PrimaryKeyConstraint();
            case "AUTO_INCREMENT":
                advance();
                return new AutoIncrementConstraint();
            case "NOT NULL":
                advance();
                consumeConstraintKeyword("NULL");
                return new NotNullConstraint();
            case "DEFAULT":
                advance();
                return new DefaultConstraint(parseDefaultValue());
            default:
                throw new SyntaxException("Unsupported column constraint '" + keyword + "'");
        }
    }

    private String parseDefaultValue() throws SyntaxException {
        if (currentToken == null
                || isCurrentValue(",")
                || isCurrentValue(")")) {
            throw new SyntaxException("Expected value after DEFAULT");
        }

        StringBuilder value = new StringBuilder();
        if (isCurrentValue("+") || isCurrentValue("-")) {
            value.append(currentToken.get("value"));
            advance();
        }

        if (!isLiteralToken(currentToken)) {
            throw new SyntaxException("Expected literal value after DEFAULT");
        }

        value.append(currentToken.get("value"));
        advance();
        return value.toString();
    }

    private void consumeConstraintKeyword(String expected) throws SyntaxException {
        if (currentToken == null
                || !expected.equalsIgnoreCase(String.valueOf(currentToken.get("value")))) {
            String actual = currentToken == null ? "end of statement" : String.valueOf(currentToken.get("value"));
            throw new SyntaxException("Expected " + expected + " in column constraint, got " + actual);
        }
        advance();
    }

    private String currentKeyword() {
        return currentToken == null
                ? ""
                : String.valueOf(currentToken.get("value")).toUpperCase(Locale.ROOT);
    }

    private List<Integer> parseTypeParameters() throws SyntaxException {
        expect("(");
        List<Integer> parameters = new ArrayList<>();
        boolean expectingNumber = true;

        while (currentToken != null && !isCurrentValue(")")) {
            if (expectingNumber) {
                if (!"number".equals(currentToken.get("type"))) {
                    throw new SyntaxException("Expected numeric type parameter");
                }
                String parameter = String.valueOf(currentToken.get("value"));
                try {
                    parameters.add(Integer.parseInt(parameter));
                } catch (NumberFormatException e) {
                    throw new SyntaxException("Expected integer type parameter, got " + parameter);
                }
                advance();
                expectingNumber = false;
            } else {
                if (!isCurrentValue(",")) {
                    throw new SyntaxException("Expected ',' or ')' in type parameters");
                }
                advance();
                expectingNumber = true;
            }
        }

        if (currentToken == null || expectingNumber) {
            throw new SyntaxException("Unclosed or empty type parameters");
        }

        expect(")");
        return parameters;
    }

    private boolean isCurrentValue(String value) {
        return currentToken != null && value.equals(currentValue());
    }

    private boolean isCurrentKeyword(String keyword) {
        return currentToken != null
                && String.valueOf(currentToken.get("value")).equalsIgnoreCase(keyword);
    }

    private int tokenLine(Map<String, Object> token) {
        Object value = token == null ? null : token.get("line");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int tokenColumn(Map<String, Object> token) {
        Object value = token == null ? null : token.get("startColumn");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String currentValue() {
        return currentToken == null ? "" : String.valueOf(currentToken.get("value"));
    }

    private String currentType() {
        return currentToken == null ? "" : String.valueOf(currentToken.get("type"));
    }

    private boolean isLiteralToken(Map<String, Object> token) {
        if (token == null) {
            return false;
        }

        String type = String.valueOf(token.get("type"));
        if (Set.of("number", "string", "identifier").contains(type)) {
            return true;
        }

        return "keyword".equals(type)
                && "NULL".equalsIgnoreCase(String.valueOf(token.get("value")));
    }

    private List<Object> parseSelectList() throws SyntaxException {
        List<Object> items = new ArrayList<>();

        if (isCurrentValue("*")) {
            advance();
            items.add("*");
            return items;
        }

        while (true) {
            Object item = parseSelectItem();
            items.add(item);

            if (!isCurrentValue(",")) {
                break;
            }
            advance(); // 跳过逗号
        }

        return items;
    }
    private Object parseSelectItem() throws SyntaxException {
        if (isCurrentAggregateFunction()) {
            return parseAggregateFunction();
        }

        String columnName = parseQualifiedIdentifier("Expected column name or table.column");

//        // 检查是否有AS别名
//        if (currentToken.getType() == TokenType.AS) {
//            advance();
//            if (currentToken.getType() != TokenType.IDENTIFIER) {
//                throw new SyntaxException(
//                        "Expected identifier after AS",
//                        currentToken.getLine(),
//                        currentToken.getColumn()
//                );
//            }
//            String alias = currentToken.getValue();
//            advance();
//            return new SelectItem(columnName, alias);
//        }

        return columnName;
    }

    private AggregateFunction parseAggregateFunction() throws SyntaxException {
        String functionType = currentKeyword();
        advance();
        expect("(");

        String identifier;
        if (isCurrentValue("*")) {
            identifier = currentValue();
            advance();
        } else if ("identifier".equals(currentType())) {
            identifier = parseQualifiedIdentifier("Expected identifier in " + functionType);
        } else {
            throw new SyntaxException(
                    String.format("Expected identifier in %s, got %s",
                            functionType, currentType()),
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }

        expect(")");
        return new AggregateFunction(functionType, identifier);
    }

    private boolean isCurrentAggregateFunction() {
        return "keyword".equals(currentType())
                && AGGREGATE_FUNCTIONS.contains(currentKeyword());
    }

    private String parseTableName() throws SyntaxException {
        if (!"identifier".equals(currentType())) {
            throw new SyntaxException(
                    String.format("Expected identifier, got %s", currentType()),
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }

        return parseQualifiedIdentifier("Expected identifier");
    }

    private String parseQualifiedIdentifier(String errorMessage) throws SyntaxException {
        if (!"identifier".equals(currentType())) {
            throw new SyntaxException(
                    String.format("%s, got %s", errorMessage, currentType()),
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }

        StringBuilder identifier = new StringBuilder(currentValue());
        advance();
        if (isCurrentValue(".")) {
            advance();
            if (!"identifier".equals(currentType())) {
                throw new SyntaxException(
                        "Expected identifier after '.'",
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }
            identifier.append(".").append(currentValue());
            advance();
        }
        if ("identifier".equals(currentType())) {
            throw new SyntaxException(
                    "Unexpected identifier after column name",
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }
        return identifier.toString();
    }
    private String parseWhereClause() throws SyntaxException {
        expect("WHERE");
        if (currentToken == null || isCurrentValue(";")) {
            throw new SyntaxException(
                    "Expected condition after WHERE",
                    tokenLine(currentToken),
                    tokenColumn(currentToken)
            );
        }

        List<String> values = new ArrayList<>();
        while (currentToken != null
                && !isCurrentValue(";")
                && !isCurrentKeyword("GROUP")
                && !isCurrentKeyword("ORDER")) {
            values.add(currentValue());
            advance();
        }
        return renderSqlFragment(values);
    }

    private String parseGroupByClause() throws SyntaxException {
        expect("GROUP");
        expect("BY");

        List<String> groupItems = new ArrayList<>();
        while (true) {
            groupItems.add(parseQualifiedIdentifier("Expected group column"));

            if (isCurrentValue(",")) {
                advance();
                if (currentToken == null
                        || isCurrentValue(";")
                        || isCurrentKeyword("ORDER")) {
                    throw new SyntaxException(
                            "Expected group column after comma",
                            tokenLine(currentToken),
                            tokenColumn(currentToken)
                    );
                }
                continue;
            }

            if (currentToken != null
                    && !isCurrentValue(";")
                    && !isCurrentKeyword("ORDER")) {
                throw new SyntaxException(
                        String.format(
                                "Expected ',' or end of statement after GROUP BY item, got %s",
                                currentValue()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }
            return String.join(", ", groupItems);
        }
    }

    private String parseOrderByClause() throws SyntaxException {
        expect("ORDER");
        expect("BY");

        List<String> sortItems = new ArrayList<>();
        while (true) {
            if (!"identifier".equals(currentType())) {
                throw new SyntaxException(
                        String.format("Expected sort column, got %s", currentType()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }

            StringBuilder sortItem = new StringBuilder(
                    parseQualifiedIdentifier("Expected sort column"));
            if (isCurrentKeyword("ASC") || isCurrentKeyword("DESC")) {
                sortItem.append(" ").append(currentValue());
                advance();
            }
            sortItems.add(sortItem.toString());

            if (isCurrentValue(",")) {
                advance();
                if (currentToken == null || isCurrentValue(";")) {
                    throw new SyntaxException(
                            "Expected sort column after comma",
                            tokenLine(currentToken),
                            tokenColumn(currentToken)
                    );
                }
                continue;
            }

            if (currentToken != null && !isCurrentValue(";")) {
                throw new SyntaxException(
                        String.format("Expected ',' or end of statement after ORDER BY item, got %s",
                                currentValue()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }
            return String.join(", ", sortItems);
        }
    }

    private String renderSqlFragment(List<String> values) {
        return String.join(" ", values)
                .replace("( ", "(")
                .replace(" )", ")")
                .replace(" . ", ".")
                .replaceAll("([+-])\\s+(\\d)", "$1$2");
    }

    public InsertStatement parseInsert() throws SyntaxException {
        try {
            return parseInsertInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw syntaxException("Unable to parse INSERT statement", e);
        }
    }

    private InsertStatement parseInsertInternal() throws SyntaxException {
        expect("INSERT");
        expect("INTO");
        String tableName = parseTableName();
        List<String> columns = new ArrayList<>();
        if (isCurrentValue("(")) {
            columns = parseColumnList();
        }
        expect("VALUES");
        List<String> values = parseValueList();
        return new InsertStatement(tableName, columns, values);
    }
    public UpdateStatement parseUpdate() throws SyntaxException {
        try {
            return parseUpdateInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw syntaxException("Unable to parse UPDATE statement", e);
        }
    }

    private UpdateStatement parseUpdateInternal() throws SyntaxException {
        expect("UPDATE");
        String tableName = parseTableName();
        expect("SET");
        List<Assignment> assignments = parseAssignmentList();
        String whereClause = null;
        if (isCurrentKeyword("WHERE")) {
            whereClause = parseWhereClause();
        }
        return new UpdateStatement(tableName, assignments, whereClause);
    }
    public DeleteStatement parseDelete() throws SyntaxException {
        try {
            return parseDeleteInternal();
        } catch (SyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw syntaxException("Unable to parse DELETE statement", e);
        }
    }

    private DeleteStatement parseDeleteInternal() throws SyntaxException {
        expect("DELETE");
        expect("FROM");
        String tableName = parseTableName();
        String whereClause = null;
        if (isCurrentKeyword("WHERE")) {
            whereClause = parseWhereClause();
        }
        return new DeleteStatement(tableName, whereClause);
    }
    private List<String> parseColumnList() throws SyntaxException {
        expect("(");
        List<String> columns = new ArrayList<>();

        while (true) {
            if (!"identifier".equals(currentType())) {
                throw new SyntaxException(
                        String.format("Expected identifier, got %s", currentType()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }

            columns.add((String) currentToken.get("value"));
            advance();

            if (isCurrentValue(")")) {
                break;
            }
            expect(",");
        }

        expect(")");
        return columns;
    }
    private List<String> parseValueList() throws SyntaxException {
        expect("(");
        List<String> values = new ArrayList<>();

        while (true) {
            if (!isLiteralToken(currentToken)) {
                throw new SyntaxException(
                        String.format("Expected literal, got %s", currentType()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }

            values.add((String) currentToken.get("value"));
            advance();

            if (isCurrentValue(")")) {
                break;
            }
            expect(",");
        }

        expect(")");
        return values;
    }
    private List<Assignment> parseAssignmentList() throws SyntaxException {
        List<Assignment> assignments = new ArrayList<>();

        while (true) {
            if (!"identifier".equals(currentType())) {
                throw new SyntaxException(
                        String.format("Expected identifier, got %s", currentType()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }

            String columnName = (String) currentToken.get("value");
            advance();
            expect("=");

            if (!isLiteralToken(currentToken)) {
                throw new SyntaxException(
                        String.format("Expected literal, got %s", currentType()),
                        tokenLine(currentToken),
                        tokenColumn(currentToken)
                );
            }

            String value = (String) currentToken.get("value");
            advance();
            assignments.add(new Assignment(columnName, value));

            if (!isCurrentValue(",")) {
                break;
            }
            advance();
        }

        return assignments;
    }

    private SyntaxException syntaxException(String message, RuntimeException cause) {
        return new SyntaxException(
                message + ": " + exceptionMessage(cause),
                tokenLine(currentToken),
                tokenColumn(currentToken),
                cause
        );
    }

    private String exceptionMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

}
