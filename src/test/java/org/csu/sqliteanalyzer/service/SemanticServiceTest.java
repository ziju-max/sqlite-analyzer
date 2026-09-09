package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.exception.SemanticException;
import org.csu.sqliteanalyzer.services.LexerService;
import org.csu.sqliteanalyzer.services.ParserService;
import org.csu.sqliteanalyzer.services.SemanticService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SemanticServiceTest {
    private final LexerService lexerService = new LexerService();
    private final SemanticService semanticService = new SemanticService();

    @Test
    public void testAnalyzeStatementsWithoutSchema() throws Exception {
        System.out.println(analyze("SELECT username FROM user WHERE user_id=1;").toSQL());
        assertDoesNotThrow(() -> analyze("INSERT INTO user(user_id,username) VALUES (1,admin);"));
        assertDoesNotThrow(() -> analyze("UPDATE user SET username=admin WHERE user_id=1;"));
        assertDoesNotThrow(() -> analyze("DELETE FROM user WHERE user_id=1;"));
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

    private ASTNode analyze(String source) throws Exception {
        ParserService parserService = new ParserService(lexerService.tokenize(source));
        return semanticService.analyze(parserService.parse());
    }
}
