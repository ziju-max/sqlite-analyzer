package org.csu.sqliteanalyzer.services;

import org.csu.sqliteanalyzer.exception.PlanException;
import org.csu.sqliteanalyzer.logical_plan.filter.AndExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.logical_plan.filter.ComparisonExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.NotExpr;
import org.csu.sqliteanalyzer.logical_plan.filter.OrExpr;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在执行计划生成阶段解析 WHERE 条件，保持 OR < AND < NOT 的优先级。
 */
public class WhereClauseExpressionParser {
    private static final Set<String> COMPARISON_OPERATORS = Set.of(
            "<", "<=", ">", ">=", "=", "!="
    );

    private final List<Map<String, Object>> tokens;
    private int pos;
    private Map<String, Object> currentToken;

    public WhereClauseExpressionParser(List<Map<String, Object>> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.currentToken = tokens.isEmpty() ? null : tokens.get(0);
    }

    public BooleanExpression parse() throws PlanException {
        if (currentToken == null) {
            throw new PlanException("Empty WHERE clause");
        }

        BooleanExpression expression = parseBooleanExpression();
        if (currentToken != null) {
            throw new PlanException(
                    String.format("Unexpected token '%s' after WHERE clause", currentValue())
            );
        }
        return expression;
    }

    private BooleanExpression parseBooleanExpression() throws PlanException {
        return parseOrExpression();
    }

    private BooleanExpression parseOrExpression() throws PlanException {
        BooleanExpression expression = parseAndExpression();

        while (isCurrentKeyword("OR")) {
            advance();
            expression = new OrExpr(expression, parseAndExpression());
        }

        return expression;
    }

    private BooleanExpression parseAndExpression() throws PlanException {
        BooleanExpression expression = parseNotExpression();

        while (isCurrentKeyword("AND")) {
            advance();
            expression = new AndExpr(expression, parseNotExpression());
        }

        return expression;
    }

    private BooleanExpression parseNotExpression() throws PlanException {
        if (isCurrentKeyword("NOT")) {
            advance();
            return new NotExpr(parseNotExpression());
        }

        return parsePrimaryExpression();
    }

    private BooleanExpression parsePrimaryExpression() throws PlanException {
        if (!isCurrentValue("(")) {
            return parseComparisonExpression();
        }

        advance();
        if (currentToken == null || isCurrentValue(")")) {
            throw new PlanException("Expected boolean expression after '('");
        }

        BooleanExpression expression = parseBooleanExpression();
        if (!isCurrentValue(")")) {
            throw new PlanException(
                    String.format("Expected ')' in WHERE clause, got %s",
                            currentToken == null ? "end of clause" : currentValue())
            );
        }

        advance();
        return expression;
    }

    private ComparisonExpr parseComparisonExpression() throws PlanException {
        if (!"identifier".equals(currentType())) {
            throw new PlanException(
                    String.format("Expected identifier in WHERE clause, got %s", currentType())
            );
        }

        String leftOperand = currentValue();
        advance();

        if (!isComparisonOperator(currentToken)) {
            throw new PlanException(
                    String.format("Expected comparison operator in WHERE clause, got %s",
                            currentToken == null ? "end of statement" : currentValue())
            );
        }

        String operator = currentValue();
        advance();

        String rightOperand = parseComparisonOperand();

        return new ComparisonExpr(leftOperand, operator, rightOperand);
    }

    private String parseComparisonOperand() throws PlanException {
        String sign = "";
        if (isCurrentValue("+") || isCurrentValue("-")) {
            sign = currentValue();
            advance();
            if (currentToken == null || !"number".equals(currentType())) {
                throw new PlanException("Expected numeric literal after sign in WHERE clause");
            }
        }

        if (!isLiteralToken(currentToken)) {
            throw new PlanException(
                    String.format("Expected literal in WHERE clause, got %s", currentType())
            );
        }

        String operand = sign + currentValue();
        advance();
        return operand;
    }

    private boolean isComparisonOperator(Map<String, Object> token) {
        return token != null
                && "operator".equals(token.get("type"))
                && COMPARISON_OPERATORS.contains(String.valueOf(token.get("value")));
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

    private boolean isCurrentValue(String value) {
        return currentToken != null && value.equals(currentValue());
    }

    private boolean isCurrentKeyword(String keyword) {
        return currentToken != null
                && String.valueOf(currentToken.get("value")).equalsIgnoreCase(keyword);
    }

    private String currentValue() {
        return currentToken == null ? "" : String.valueOf(currentToken.get("value"));
    }

    private String currentType() {
        return currentToken == null ? "" : String.valueOf(currentToken.get("type"));
    }

    private void advance() {
        pos++;
        currentToken = pos < tokens.size() ? tokens.get(pos) : null;
    }
}
