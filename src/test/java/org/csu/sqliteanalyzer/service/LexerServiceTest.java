package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.analyzer.exception.LexerException;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LexerServiceTest {
    private final LexerService lexerService = new LexerService();

    @Test
    public void tokenizesCaseInsensitiveKeywordsAndIdentifiers() {
        String source = "insert into user (user_id,username) values(1,zi);";
        source = """
                create table user(
                    id int,
                    username varchar(30)
                );
                """;
        List<Map<String, Object>> tokens = lexerService.tokenize(source);
        tokens.forEach(System.out::println);

    }

    @Test
    public void recognizesDataTypesSeparatelyFromOperationKeywords() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "cReAtE tAbLe account (id iNt, name vArChAr, enabled BoOlEaN);"
        );
        tokens.forEach(System.out::println);

        assertEquals("keyword", tokens.get(0).get("type"));
        assertEquals("CREATE", tokens.get(0).get("value"));
        assertEquals("keyword", tokens.get(1).get("type"));
        assertEquals("TABLE", tokens.get(1).get("value"));
        assertEquals("identifier", tokens.get(2).get("type"));
        assertEquals("account", tokens.get(2).get("value"));
        assertEquals("identifier", tokens.get(4).get("type"));
        assertEquals("id", tokens.get(4).get("value"));
        assertEquals("datatype", tokens.get(5).get("type"));
        assertEquals("INT", tokens.get(5).get("value"));
        assertEquals("identifier", tokens.get(7).get("type"));
        assertEquals("name", tokens.get(7).get("value"));
        assertEquals("datatype", tokens.get(8).get("type"));
        assertEquals("VARCHAR", tokens.get(8).get("value"));
        assertEquals("identifier", tokens.get(10).get("type"));
        assertEquals("enabled", tokens.get(10).get("value"));
        assertEquals("datatype", tokens.get(11).get("type"));
        assertEquals("BOOLEAN", tokens.get(11).get("value"));
    }

    @Test
    public void recognizesOrderByAndSortDirectionKeywords() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "SELECT name FROM user ORDER BY age DESC, name ASC;"
        );

        assertEquals(List.of(
                        "SELECT", "name", "FROM", "user", "ORDER", "BY",
                        "age", "DESC", ",", "name", "ASC", ";"
                ),
                tokens.stream().map(token -> token.get("value")).toList());
        assertEquals("keyword", tokens.get(4).get("type"));
        assertEquals("keyword", tokens.get(5).get("type"));
        assertEquals("keyword", tokens.get(7).get("type"));
        assertEquals("keyword", tokens.get(10).get("type"));
    }

    @Test
    public void recognizesAggregateFunctionKeywords() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "SELECT COUNT(age), SUM(score), AVG(score), MAX(score), MIN(score) FROM user;"
        );

        assertEquals(List.of("SELECT", "COUNT", "SUM", "AVG", "MAX", "MIN", "FROM"),
                tokens.stream()
                        .filter(token -> "keyword".equals(token.get("type")))
                        .map(token -> token.get("value"))
                        .toList());
        assertEquals("keyword", tokens.get(1).get("type"));
        assertEquals("keyword", tokens.get(21).get("type"));
    }

    @Test
    public void recognizesGroupByKeyword() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "SELECT age FROM user GROUP BY age;"
        );

        assertEquals("GROUP", tokens.get(4).get("value"));
        assertEquals("keyword", tokens.get(4).get("type"));
        assertEquals("BY", tokens.get(5).get("value"));
        assertEquals("keyword", tokens.get(5).get("type"));
    }

    @Test
    public void rejectsUnterminatedQuotedLiterals() {
        assertUnterminatedQuote("SELECT 'value");
        assertUnterminatedQuote("SELECT \"value");
        assertUnterminatedQuote("SELECT `value");
    }

    @Test
    public void rejectsUnterminatedBlockComment() {
        LexerException exception = assertThrows(
                LexerException.class,
                () -> lexerService.tokenize("SELECT /* comment")
        );

        assertEquals(1, exception.getLine());
        assertEquals(8, exception.getColumn());
    }

    @Test
    public void rejectsInvalidNumericLiterals() {
        LexerException l1= assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT 1.2.3"));
        System.out.println(l1.getLine()+" "+l1.getColumn());
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT 12abc"));
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT 1e+"));
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT 0x"));
    }

    @Test
    public void acceptsSupportedNumericLiterals() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "1 1. 0.5 1e-3 0xFF"
        );

        assertEquals(List.of("1", "1.", "0.5", "1e-3", "0xFF"),
                tokens.stream().map(token -> token.get("value")).toList());
    }

    @Test
    public void acceptsConfiguredPunctuationOperatorsAndComments() {
        List<Map<String, Object>> tokens = lexerService.tokenize(
                "SELECT `name`, 'can\\'t', \"quoted\", table.column, 1 + 2 - 3 * 4 / 5; # line comment\n"
                        + "/* block comment */ SELECT name FROM table;"
        );

        assertEquals(List.of(
                        "SELECT", "`name`", ",", "'can\\'t'", ",", "\"quoted\"", ",",
                        "TABLE", ".", "column", ",", "1", "+", "2", "-", "3", "*", "4", "/", "5", ";",
                        "SELECT", "name", "FROM", "TABLE", ";"
                ),
                tokens.stream().map(token -> token.get("value")).toList());
    }

    @Test
    public void acceptsBackslashAsAnExplicitOperator() {
        List<Map<String, Object>> tokens = lexerService.tokenize("\\");

        assertEquals("operator", tokens.get(0).get("type"));
        assertEquals("\\", tokens.get(0).get("value"));
    }

    @Test
    public void rejectsUnexpectedCharacters() {
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT @name"));
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT $name"));
        assertThrows(LexerException.class, () -> lexerService.tokenize("SELECT name:name"));
    }

    private void assertUnterminatedQuote(String source) {
        LexerException exception = assertThrows(
                LexerException.class,
                () -> lexerService.tokenize(source)
        );
        assertEquals(1, exception.getLine());
        assertEquals(8, exception.getColumn());
    }
}
