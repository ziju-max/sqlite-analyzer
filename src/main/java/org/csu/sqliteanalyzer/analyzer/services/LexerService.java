package org.csu.sqliteanalyzer.analyzer.services;

import org.csu.sqliteanalyzer.analyzer.exception.LexerException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LexerService {

    private static final Set<String> OPERATION_KEYWORDS = Set.of(
            "CREATE", "TABLE", "INSERT", "INTO", "SELECT", "FROM", "WHERE", "DELETE", "VALUES",
            "UPDATE", "SET",
            "AND", "OR", "NOT",
            "ORDER", "BY", "ASC", "DESC", "GROUP",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "ON",
            "COUNT", "SUM", "AVG", "MAX", "MIN",
            "PRIMARY", "KEY", "AUTOINCREMENT", "NULL", "DEFAULT"
    );
    private static final Set<String> DATA_TYPES = Set.of(
            "INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT",
            "INT2", "INT8",
            "CHAR", "CHARACTER", "VARCHAR", "VARYING", "NCHAR", "NVARCHAR",
            "TEXT", "CLOB",
            "BLOB",
            "REAL", "DOUBLE", "FLOAT",
            "NUMERIC", "DECIMAL", "BOOLEAN",
            "DATE", "DATETIME"
    );
    private static final Set<String> MULTI_CHARACTER_OPERATORS = Set.of(
            "==", "!=", "<=", ">="
    );

    public List<Map<String, Object>> tokenize(String source) {
        try {
            return tokenizeInternal(source);
        } catch (LexerException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LexerException("Unable to tokenize SQL: " + exceptionMessage(e), 0, 0, e);
        }
    }

    private List<Map<String, Object>> tokenizeInternal(String source) {
        Objects.requireNonNull(source, "source must not be null");

        List<Map<String, Object>> tokens = new ArrayList<>();
        int position = 0;
        SourceLocation location = new SourceLocation();

        while (position < source.length()) {
            char current = source.charAt(position);

            if (Character.isWhitespace(current)) {
                location.advance(current);
                position++;
                continue;
            }

            if (current == '-' && position + 1 < source.length()
                    && source.charAt(position + 1) == '-') {
                int nextPosition = skipLineComment(source, position + 2);
                location.advance(source, position, nextPosition);
                position = nextPosition;
                continue;
            }

            if (current == '/' && position + 1 < source.length()
                    && source.charAt(position + 1) == '*') {
                int nextPosition = skipBlockComment(
                        source, position + 2, location.line, location.column);
                location.advance(source, position, nextPosition);
                position = nextPosition;
                continue;
            }

            int start = position;
            int line = location.line;
            int startColumn = location.column;

            if (isIdentifierStart(current)) {
                position = readIdentifier(source, position);
                String value = source.substring(start, position);
                String upperCaseValue = value.toUpperCase(Locale.ROOT);
                String type;
                if (OPERATION_KEYWORDS.contains(upperCaseValue)) {
                    type = "keyword";
                } else if (DATA_TYPES.contains(upperCaseValue)) {
                    type = "datatype";
                } else {
                    type = "identifier";
                }
                location.advance(source, start, position);
                tokens.add(token(type,
                        ("keyword".equals(type) || "datatype".equals(type))
                                ? upperCaseValue
                                : value,
                        line,
                        startColumn));
                continue;
            }

            if (current == '\'' || current == '"' || current == '`') {
                position = readQuoted(source, position, current, line, startColumn);
                String type = current == '\'' ? "string" : "identifier";
                location.advance(source, start, position);
                tokens.add(token(type, source.substring(start, position),
                        line, startColumn));
                continue;
            }

            if (isNumberStart(source, position)) {
                position = readNumber(source, position, line, startColumn);
                location.advance(source, start, position);
                tokens.add(token("number", source.substring(start, position),
                        line, startColumn));
                continue;
            }

            String operator = readOperator(source, position);
            if (operator != null) {
                position += operator.length();
                location.advance(source, start, position);
                tokens.add(token("operator", operator, line, startColumn));
                continue;
            }

            if (isPunctuation(current)) {
                position++;
                location.advance(current);
                tokens.add(token("delimiter", String.valueOf(current),
                        line, startColumn));
                continue;
            }

            throw new LexerException(
                    String.format("Unexpected character '%s'", current),
                    line,
                    startColumn
            );
        }

        return tokens;
    }

    private String exceptionMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static Map<String, Object> token(
            String type,
            String value,
            int line,
            int startColumn
    ) {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("type", type);
        token.put("value", value);
        token.put("line", line);
        token.put("startColumn", startColumn);
        return token;
    }

    private static final class SourceLocation {

        private int line = 1;
        private int column = 1;
        private boolean previousWasCarriageReturn;

        private void advance(char character) {
            if (character == '\r') {
                line++;
                column = 1;
                previousWasCarriageReturn = true;
            } else if (character == '\n') {
                if (!previousWasCarriageReturn) {
                    line++;
                    column = 1;
                }
                previousWasCarriageReturn = false;
            } else {
                column++;
                previousWasCarriageReturn = false;
            }
        }

        private void advance(String source, int start, int end) {
            for (int index = start; index < end; index++) {
                advance(source.charAt(index));
            }
        }
    }

    private static int readIdentifier(String source, int position) {
        position++;
        while (position < source.length() && isIdentifierPart(source.charAt(position))) {
            position++;
        }
        return position;
    }

    private static int readQuoted(
            String source,
            int position,
            char closing,
            int line,
            int column
    ) {
        position++;
        while (position < source.length()) {
            if (source.charAt(position) == closing) {
                if (position + 1 < source.length() && source.charAt(position + 1) == closing) {
                    position += 2;
                    continue;
                }
                return position + 1;
            }
            position++;
        }

        throw new LexerException(
                String.format("Unterminated %s-quoted literal", quoteName(closing)),
                line,
                column
        );
    }

    private static int readNumber(String source, int position, int line, int column) {
        int start = position;

        if (source.charAt(position) == '0'
                && position + 1 < source.length()
                && (source.charAt(position + 1) == 'x'
                || source.charAt(position + 1) == 'X')) {
            position += 2;
            int digitsStart = position;
            while (position < source.length() && isHexDigit(source.charAt(position))) {
                position++;
            }
            if (position == digitsStart) {
                throw invalidNumber(source, start, position, line, column);
            }
            ensureNumberBoundary(source, start, position, line, column);
            return position;
        }

        if (source.charAt(position) == '.') {
            position++;
            while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                position++;
            }
        } else {
            while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                position++;
            }

            if (position < source.length() && source.charAt(position) == '.') {
                position++;
                while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                    position++;
                }
            }
        }

        if (position < source.length()
                && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
            position++;
            if (position < source.length()
                    && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
                position++;
            }
            int exponentDigitsStart = position;
            while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                position++;
            }
            if (position == exponentDigitsStart) {
                throw invalidNumber(source, start, position, line, column);
            }
        }

        ensureNumberBoundary(source, start, position, line, column);
        return position;
    }

    private static void ensureNumberBoundary(
            String source,
            int start,
            int position,
            int line,
            int column
    ) {
        if (position < source.length()
                && (isIdentifierPart(source.charAt(position)) || source.charAt(position) == '.')) {
            int end = position + 1;
            while (end < source.length()
                    && (isIdentifierPart(source.charAt(end)) || source.charAt(end) == '.')) {
                end++;
            }
            throw invalidNumber(source, start, end, line, column);
        }
    }

    private static LexerException invalidNumber(
            String source,
            int start,
            int end,
            int line,
            int column
    ) {
        int safeEnd = Math.min(Math.max(end, start + 1), source.length());
        return new LexerException(
                String.format("Invalid numeric literal '%s'", source.substring(start, safeEnd)),
                line,
                column
        );
    }

    private static String readOperator(String source, int position) {
        if (position + 1 < source.length()) {
            String twoCharacters = source.substring(position, position + 2);
            if (MULTI_CHARACTER_OPERATORS.contains(twoCharacters)) {
                return twoCharacters;
            }
        }

        return switch (source.charAt(position)) {
            case '=', '!', '<', '>', '+', '-', '*', '/'->
                    String.valueOf(source.charAt(position));
            default -> null;
        };
    }

    private static int skipLineComment(String source, int position) {
        while (position < source.length()
                && source.charAt(position) != '\r'
                && source.charAt(position) != '\n') {
            position++;
        }
        return position;
    }

    private static int skipBlockComment(String source, int position, int line, int column) {
        while (position + 1 < source.length()) {
            if (source.charAt(position) == '*' && source.charAt(position + 1) == '/') {
                return position + 2;
            }
            position++;
        }
        throw new LexerException("Unterminated block comment", line, column);
    }

    private static String quoteName(char quote) {
        return switch (quote) {
            case '\'' -> "single";
            case '"' -> "double";
            case '`' -> "backtick";
            default -> "quoted";
        };
    }

    private static boolean isNumberStart(String source, int position) {
        char current = source.charAt(position);
        return isAsciiDigit(current)
                || (current == '.'
                && position + 1 < source.length()
                && isAsciiDigit(source.charAt(position + 1)));
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isHexDigit(char character) {
        return isAsciiDigit(character)
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_' || character == '$';
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character)
                || character == '_'
                || character == '$';
    }

    private static boolean isPunctuation(char character) {
        return switch (character) {
            case '(', ')', ',', ';', '.' -> true;
            default -> false;
        };
    }
}
