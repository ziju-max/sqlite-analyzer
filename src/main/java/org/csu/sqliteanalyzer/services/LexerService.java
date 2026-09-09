package org.csu.sqliteanalyzer.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class LexerService {

    private static final Set<String> KEYWORDS = Set.of(
        "CREATE","TABLE","INSERT","INTO","SELECT","FROM","WHERE","DELETE","VALUES",
            "UPDATE","SET"
    );
    private static final Set<String> OPERATIONS = Set.of(
        "=","!=","<","<=",">",">=","+","-","*","/"
    );
    private static final Set<String> DELIMITERS = Set.of(
            "(",")",";",","
    );


    public List<Map<String, Object>> tokenize(String source) {
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
                int nextPosition = skipBlockComment(source, position + 2);
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
                String type = KEYWORDS.contains(value.toUpperCase(Locale.ROOT))
                        ? "keyword"
                        : "identifier";
                location.advance(source, start, position);
                tokens.add(token(type, value, line, startColumn));
                continue;
            }

//            if (current == '\'' || current == '"' || current == '`' || current == '[') {
//                char closing = current == '[' ? ']' : current;
//                position = readQuoted(source, position, closing);
//                String type = current == '\'' ? "string" : "identifier";
//                location.advance(source, start, position);
//                tokens.add(token(type, source.substring(start, position),
//                        line, startColumn));
//                continue;
//            }

            if (Character.isDigit(current)
                    || (current == '.' && position + 1 < source.length()
                    && Character.isDigit(source.charAt(position + 1)))) {
                position = readNumber(source, position);
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

            position++;
            location.advance(current);
            tokens.add(token("unknown", String.valueOf(current),
                    line, startColumn));
        }

        return tokens;
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

    private static int readQuoted(String source, int position, char closing) {
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
        return position;
    }

    private static int readNumber(String source, int position) {
        while (position < source.length() && Character.isDigit(source.charAt(position))) {
            position++;
        }

        if (position < source.length() && source.charAt(position) == '.') {
            position++;
            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                position++;
            }
        }

        if (position < source.length()
                && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
            int exponentStart = position++;
            if (position < source.length()
                    && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
                position++;
            }
            int exponentDigitsStart = position;
            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                position++;
            }
            if (position == exponentDigitsStart) {
                position = exponentStart;
            }
        }

        return position;
    }

    private static String readOperator(String source, int position) {
        if (position + 1 < source.length()) {
            String twoCharacters = source.substring(position, position + 2);
            if (Set.of("==", "!=", "<>", "<=", ">=", "||", "<<", ">>", "->").contains(twoCharacters)) {
                return twoCharacters;
            }
        }

        return switch (source.charAt(position)) {
            case '=', '!', '<', '>', '+', '-', '*', '/', '%', '|', '&', '~' ->
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

    private static int skipBlockComment(String source, int position) {
        while (position + 1 < source.length()) {
            if (source.charAt(position) == '*' && source.charAt(position + 1) == '/') {
                return position + 2;
            }
            position++;
        }
        return source.length();
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
            case '(', ')', ',', ';', '.', ':' -> true;
            default -> false;
        };
    }
}
