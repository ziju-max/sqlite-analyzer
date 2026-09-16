package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.exception.SemanticException;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.ParserService;
import org.csu.sqliteanalyzer.analyzer.services.SemanticService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SemanticServiceTest {
    private final LexerService lexerService = new LexerService();
    private final SemanticService semanticService = new SemanticService();

    @Test
    public void testAnalyzeStatementsWithoutSchema() throws Exception {
        System.out.println(analyze("SELECT username FROM user WHERE user_id=1;"));
//        assertDoesNotThrow(() -> analyze("INSERT INTO user(user_id,username) VALUES (1,admin);"));
//        assertDoesNotThrow(() -> analyze("UPDATE user SET username=admin WHERE user_id=1;"));
//        assertDoesNotThrow(() -> analyze("DELETE FROM user WHERE user_id=1;"));
    }

    @Test
    public void test1(){
        System.out.println(semanticService.getTables());
    }

    @Test
    public void testAnalyzeStatementsWithSchema() throws Exception {
        semanticService.registerTable("user", List.of("user_id", "username"));

        assertDoesNotThrow(() -> analyze("SELECT username FROM user WHERE user_id=1;"));
        assertDoesNotThrow(() -> analyze("INSERT INTO user(user_id,username) VALUES (1,admin);"));
        assertDoesNotThrow(() -> analyze("UPDATE user SET username=admin WHERE user_id=1;"));
        assertDoesNotThrow(() -> analyze("DELETE FROM user WHERE user_id=1;"));
    }

    @Test
    public void testAnalyzeAggregateFunctionsWithSchema() throws Exception {
        semanticService.registerTable("user", List.of("age", "username"));

//        assertDoesNotThrow(() -> analyze(
//                "SELECT COUNT(age), SUM(score), AVG(score), MAX(score), MIN(score) FROM user;"
//        ));
        try {
            analyze("SELECT username FROM user " +
                    "WHERE age > 18 " +
                    "GROUP BY age;");
        }catch (Exception e){
            System.out.println(e);
        }
    }

    @Test
    public void testAnalyzeGroupByWithSchema() throws Exception {
        semanticService.registerTable("user", List.of("age", "score"));

        assertDoesNotThrow(() -> analyze(
                "SELECT age, COUNT(score) FROM user GROUP BY age;"
        ));
        assertThrows(SemanticException.class,
                () -> analyze("SELECT age FROM user GROUP BY missing;"));
    }

    @Test
    public void testRejectUnknownColumn() throws Exception {
        semanticService.registerTable("user", List.of("user_id", "username"));

        assertThrows(SemanticException.class,
                () -> analyze("SELECT password FROM user;"));
    }

    @Test
    public void testRejectInvalidInsert() throws Exception {
        semanticService.registerTable("user", List.of("user_id", "username"));

        assertThrows(SemanticException.class,
                () -> analyze("INSERT INTO user(user_id,username) VALUES (1);"));
    }

    @Test
    public void testRejectUnknownTable() throws Exception {
        semanticService.registerTable("user", List.of("user_id", "username"));

        assertThrows(SemanticException.class,
                () -> analyze("DELETE FROM account WHERE user_id=1;"));
    }

    @Test
    public void testRejectStringArithmeticWithIntColumn() throws Exception {
        semanticService.registerTable("user", Map.of(
                "age", "INT",
                "username", "VARCHAR"
        ));

        SemanticException exception = assertThrows(SemanticException.class,
                () -> analyze("SELECT age FROM user WHERE age + '1' > 18;"));

        assertTrue(exception.getMessage().contains("类型不匹配"));
        assertTrue(exception.getMessage().contains("age") || exception.getMessage().contains("INT"));
    }

    @Test
    public void testRejectNumericArithmeticWithStringColumn() throws Exception {
        semanticService.registerTable("user", Map.of(
                "age", "INT",
                "username", "VARCHAR"
        ));

        assertThrows(SemanticException.class,
                () -> analyze("SELECT username FROM user WHERE username * 2 > 0;"));
    }

    @Test
    public void testAllowNumericArithmetic() throws Exception {
        semanticService.registerTable("user", Map.of("age", "INT"));

        assertDoesNotThrow(
                () -> analyze("SELECT age FROM user WHERE age + 1 > 18;")
        );
    }

    private ASTNode analyze(String source) throws Exception {
        ParserService parserService = new ParserService(lexerService.tokenize(source));
        return semanticService.analyze(parserService.parse());
    }
}
