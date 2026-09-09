package org.csu.sqliteanalyzer.services;

import org.csu.sqliteanalyzer.ast.*;
import org.csu.sqliteanalyzer.common.Assignment;
import org.csu.sqliteanalyzer.common.SelectItem;
import org.csu.sqliteanalyzer.exception.SyntaxException;

import java.util.*;

public class ParserService {
    private List<Map<String,Object>> tokens;
    private int pos;
    private Map<String,Object> currentToken;

    public ParserService(List<Map<String,Object>> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.currentToken = tokens.isEmpty() ? null : tokens.get(0);
    }

    public ASTNode parse() throws SyntaxException {
        if (currentToken == null) {
            throw new SyntaxException("Empty SQL statement");
        }

        switch ((String) currentToken.get("value")) {
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
                        String.format("Unexpected token '%s', expected SELECT, INSERT, UPDATE, or DELETE",
                                currentToken.get("value")),
                        (int)currentToken.get("line"),
                        (int)currentToken.get("startColumn")
                );
        }
    }

    private void advance() {
        pos++;
        if (pos < tokens.size()) {
            currentToken = tokens.get(pos);
        }
    }
    private Map<String,Object> expect(String keyword) throws SyntaxException {
        if (!currentToken.get("value").equals(keyword)) {
            throw new SyntaxException(
                    String.format("Expected %s, got %s", keyword, currentToken.get("value")),
                    (int)currentToken.get("line"),
                    (int)currentToken.get("startColumn")
            );
        }
        Map<String,Object> token = currentToken;
        advance();
        return token;
    }
    public SelectStatement parseSelect() throws SyntaxException {
        expect("SELECT");
        List<Object> selectList = parseSelectList();
        expect("FROM");
        String tableName = parseTableName();
        String whereClause = null;
        if (currentToken.get("value").equals("WHERE")) {
            whereClause = parseWhereClause();
        }
        return new SelectStatement(selectList, tableName, whereClause);
    }
    private List<Object> parseSelectList() throws SyntaxException {
        List<Object> items = new ArrayList<>();

        if (currentToken.get("value") == "*") {
            advance();
            items.add("*");
            return items;
        }

        while (true) {
            Object item = parseSelectItem();
            items.add(item);

            if (currentToken.get("value") != ",") {
                break;
            }
            advance(); // 跳过逗号
        }

        return items;
    }
    private Object parseSelectItem() throws SyntaxException {
        if (currentToken.get("type") != "identifier") {
            throw new SyntaxException(
                    String.format("Expected identifier, got %s", currentToken.get("type")),
                    (int)currentToken.get("line"),
                    (int)currentToken.get("startColumn")
            );
        }

        String columnName = (String)currentToken.get("value");
        advance();

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
    private String parseTableName() throws SyntaxException {
        if (currentToken.get("type") != "identifier") {
            throw new SyntaxException(
                    String.format("Expected identifier, got %s", currentToken.get("type")),
                    (int)currentToken.get("line"),
                    (int)currentToken.get("startColumn")
            );
        }

        String tableName = (String) currentToken.get("value");
        advance();

//        // 支持 schema.table
//        if (currentToken.getType() == TokenType.DOT) {
//            advance();
//            if (currentToken.getType() != TokenType.IDENTIFIER) {
//                throw new SyntaxException(
//                        "Expected identifier after dot",
//                        currentToken.getLine(),
//                        currentToken.getColumn()
//                );
//            }
//            tableName += "." + currentToken.getValue();
//            advance();
//        }

        return tableName;
    }
    private String parseWhereClause() throws SyntaxException {
        expect("WHERE");

        // 简化实现：收集直到遇到EOF或下一个语句关键字
        StringBuilder condition = new StringBuilder();
        while (!currentToken.get("value").equals(";") &&
                !currentToken.get("value").equals("SELECT") &&
                !currentToken.get("value").equals("INSERT") &&
                !currentToken.get("value").equals("UPDATE") &&
                !currentToken.get("value").equals("DELETE")){
            condition.append(currentToken.get("value")).append(" ");
            advance();
        }

        return condition.toString().trim();
    }

    public InsertStatement parseInsert() throws SyntaxException{
        expect("INSERT");
        expect("INTO");
        String tableName = parseTableName();
        List<String> columns = new ArrayList<>();
        if (currentToken.get("value").equals("(")) {
            columns = parseColumnList();
        }
        expect("VALUES");
        List<String> values = parseValueList();
        return new InsertStatement(tableName, columns, values);
    }
    public UpdateStatement parseUpdate() throws SyntaxException{
        expect("UPDATE");
        String tableName = parseTableName();
        expect("SET");
        List<Assignment> assignments = parseAssignmentList();
        String whereClause = null;
        if (currentToken.get("value").equals("WHERE")) {
            whereClause = parseWhereClause();
        }
        return new UpdateStatement(tableName, assignments, whereClause);
    }
    public DeleteStatement parseDelete() throws SyntaxException{
        expect("DELETE");
        expect("FROM");
        String tableName = parseTableName();
        String whereClause = null;
        if (currentToken.get("value").equals("WHERE")) {
            whereClause = parseWhereClause();
        }
        return new DeleteStatement(tableName, whereClause);
    }
    private List<String> parseColumnList() throws SyntaxException {
        expect("(");
        List<String> columns = new ArrayList<>();

        while (true) {
            if (currentToken.get("type") != "identifier") {
                throw new SyntaxException(
                        String.format("Expected identifier, got %s", currentToken.get("type")),
                        (int)currentToken.get("line"),
                        (int)currentToken.get("startColumn")
                );
            }

            columns.add((String) currentToken.get("value"));
            advance();

            if (currentToken.get("value").equals(")")) {
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
            if (currentToken.get("type") != "identifier" &&
                    currentToken.get("type") != "number") {
                throw new SyntaxException(
                        String.format("Expected literal, got %s", currentToken.get("type")),
                        (int)currentToken.get("line"),
                        (int)currentToken.get("startColumn")
                );
            }

            values.add((String) currentToken.get("value"));
            advance();

            if (currentToken.get("value").equals(")")) {
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
            if (currentToken.get("type") != "identifier") {
                throw new SyntaxException(
                        String.format("Expected identifier, got %s", currentToken.get("type")),
                        (int)currentToken.get("line"),
                        (int)currentToken.get("startColumn")
                );
            }

            String columnName = (String) currentToken.get("value");
            advance();
            expect("=");

            if (currentToken.get("type") != "identifier" &&
                    currentToken.get("type") != "number") {
                throw new SyntaxException(
                        String.format("Expected literal, got %s", currentToken.get("type")),
                        (int)currentToken.get("line"),
                        (int)currentToken.get("startColumn")
                );
            }

            String value = (String) currentToken.get("value");
            advance();
            assignments.add(new Assignment(columnName, value));

            if (!currentToken.get("value").equals(",")) {
                break;
            }
            advance();
        }

        return assignments;
    }
}
