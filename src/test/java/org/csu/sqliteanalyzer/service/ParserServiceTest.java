package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.DeleteStatement;
import org.csu.sqliteanalyzer.analyzer.ast.InsertStatement;
import org.csu.sqliteanalyzer.analyzer.ast.UpdateStatement;
import org.csu.sqliteanalyzer.analyzer.ast.create.CreateTableStatement;
import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.analyzer.exception.SyntaxException;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.ParserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserServiceTest {

    private ParserService parserService;
    private LexerService lexerService;

    @Test
    public void test1(){
        String source= """
                select username from user where age>18 or depth = "fads" and home="CS";
                """;

        lexerService = new LexerService();
        parserService = new ParserService(lexerService.tokenize(source));
//        List<Map<String,Object>> tokens = lexerService.tokenize(source);
//        System.out.println(tokens.get(0));
        try {
            ASTNode ast = parserService.parse();
            if (ast instanceof SelectStatement a){
                System.out.println(a.toString());
            } else{
                System.out.println("error");
            }
        }catch (Exception e){
            System.out.println(e);
        }
    }

    @Test
    public void testParseInsert() throws Exception {
        String source = """
                INSERT INTO user(user_id,username) VALUES
                (1,admin);
                """;

        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(source)
        );

        InsertStatement insertStatement = parserService.parseInsert();

        assertEquals("user", insertStatement.getTableName());
        assertEquals(List.of("user_id", "username"), insertStatement.getColumns());
        assertEquals(List.of("1", "admin"), insertStatement.getValues());
    }

    @Test
    public void testParseCreateTable() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "CREATE TABLE user (user_id INTEGER PRIMARY KEY, username TEXT);"
                )
        );

        CreateTableStatement createStatement = assertInstanceOf(
                CreateTableStatement.class,
                parserService.parse()
        );

        assertEquals("user", createStatement.getTableName());
        assertEquals(List.of(
                "user_id INTEGER PRIMARY KEY",
                "username TEXT"
        ), createStatement.getColumns());
    }

    @Test
    public void testParseCreateTableStoresColumnList() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "CREATE TABLE user (" +
                                "user_id BIGINT NOT NULL AUTOINCREMENT, " +
                                "username VARCHAR(30) DEFAULT NULL" +
                                ");"
                )
        );

        CreateTableStatement statement = assertInstanceOf(
                CreateTableStatement.class,
                parserService.parse()
        );

        assertEquals("user", statement.getTableName());
        assertEquals(2, statement.getColumnDefinitions().size());
        assertEquals("user_id", statement.getColumnDefinitions().get(0).getName());
        assertEquals("BIGINT", statement.getColumnDefinitions().get(0).getDataType().getName());
        assertEquals(2, statement.getColumnDefinitions().get(0).getConstraints().size());
        assertEquals(List.of(
                "user_id BIGINT NOT NULL AUTOINCREMENT",
                "username VARCHAR(30) DEFAULT NULL"
        ), statement.getColumns());
    }

    @Test
    public void testParseCreateTableIfNotExists() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("CREATE TABLE IF NOT EXISTS user (id INT);")
        );

        CreateTableStatement statement = assertInstanceOf(
                CreateTableStatement.class,
                parserService.parse()
        );

        assertEquals("user", statement.getTableName());
        assertEquals(List.of("id INT"), statement.getColumns());
        assertEquals("CREATE TABLE user (id INT)", statement.toSQL());
    }

    @Test
    public void testParseVarcharLength() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("CREATE TABLE user (username VARCHAR(30));")
        );

        CreateTableStatement createStatement = assertInstanceOf(
                CreateTableStatement.class,
                parserService.parse()
        );

        assertEquals(List.of(
                "username VARCHAR(30)"
        ), createStatement.getColumns());
    }

    @Test
    public void testParseSupportedColumnConstraints() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "CREATE TABLE user (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL DEFAULT 1);"
                )
        );

        CreateTableStatement createStatement = assertInstanceOf(
                CreateTableStatement.class,
                parserService.parse()
        );
        System.out.println(createStatement);

        assertEquals(List.of(
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL DEFAULT 1"
        ), createStatement.getColumns());
    }

    @Test
    public void testRejectUnsupportedColumnConstraint() {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("CREATE TABLE user (id INTEGER UNIQUE);")
        );

        assertThrows(SyntaxException.class, () -> parserService.parse());
    }

    @Test
    public void testRejectIncompleteColumnConstraint() {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("CREATE TABLE user (id INTEGER PRIMARY);")
        );

        assertThrows(SyntaxException.class, () -> parserService.parse());
    }

    @Test
    public void testRejectInvalidDefaultKeywordLiteral() {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("CREATE TABLE user (id INTEGER DEFAULT PRIMARY);")
        );

        assertThrows(SyntaxException.class, () -> parserService.parse());
    }

    @Test
    public void testParseUpdate() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("UPDATE user SET username=admin, user_id=2 WHERE user_id=1;")
        );

        UpdateStatement updateStatement = parserService.parseUpdate();

        assertEquals("user", updateStatement.getTableName());
        assertEquals("username", updateStatement.getAssignments().get(0).getColumnName());
        assertEquals("admin", updateStatement.getAssignments().get(0).getValue());
        assertEquals("user_id", updateStatement.getAssignments().get(1).getColumnName());
        assertEquals("2", updateStatement.getAssignments().get(1).getValue());
        assertTrue(updateStatement.getWhereClause().isPresent());
        assertEquals("user_id = 1", updateStatement.getWhereClause().get());
    }

    @Test
    public void testParseDelete() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("DELETE FROM user WHERE user_id=1;")
        );

        DeleteStatement deleteStatement = parserService.parseDelete();

        assertEquals("user", deleteStatement.getTableName());
        assertTrue(deleteStatement.getWhereClause().isPresent());
        assertEquals("user_id = 1", deleteStatement.getWhereClause().get());
    }

    @Test
    public void testParseWhereClauseStoresStringOnly() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "SELECT age FROM user WHERE age > 18 AND name = 'ju' OR user_id = 92;"
                )
        );

        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                parserService.parse()
        );

        assertEquals("age > 18 AND name = 'ju' OR user_id = 92",
                selectStatement.getWhereClause().orElseThrow());
        assertEquals("SELECT age FROM user WHERE age > 18 AND name = 'ju' OR user_id = 92",
                selectStatement.toSQL());
    }

    @Test
    public void testParseNotWhereClauseStoresStringOnly() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "SELECT age FROM user WHERE NOT age > 18 AND user_id = 92;"
                )
        );

        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                parserService.parse()
        );

        assertEquals("NOT age > 18 AND user_id = 92",
                selectStatement.getWhereClause().orElseThrow());
    }

    @Test
    public void testParseOrderByAfterWhereClause() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "SELECT username FROM user WHERE age >= 18 ORDER BY age DESC, username ASC;"
                )
        );

        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                parserService.parse()
        );

        assertEquals("age >= 18", selectStatement.getWhereClause().orElseThrow());
        assertEquals("age DESC, username ASC", selectStatement.getOrderByClause().orElseThrow());
        assertEquals("SELECT username FROM user WHERE age >= 18 ORDER BY age DESC, username ASC",
                selectStatement.toSQL());
    }

    @Test
    public void testParseAggregateFunctions() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize(
                        "SELECT COUNT(age), SUM(score), AVG(score), MAX(score), MIN(score) FROM user;"
                )
        );

        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                parserService.parse()
        );

        AggregateFunction count = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(0));
        AggregateFunction sum = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(1));
        AggregateFunction avg = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(2));
        AggregateFunction max = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(3));
        AggregateFunction min = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(4));

        assertEquals("COUNT", count.getFunctionType());
        assertEquals("age", count.getIdentifier());
        assertEquals("SUM", sum.getFunctionType());
        assertEquals("score", sum.getIdentifier());
        assertEquals("AVG", avg.getFunctionType());
        assertEquals("score", avg.getIdentifier());
        assertEquals("MAX", max.getFunctionType());
        assertEquals("score", max.getIdentifier());
        assertEquals("MIN", min.getFunctionType());
        assertEquals("score", min.getIdentifier());
        assertEquals(
                "SELECT COUNT(age), SUM(score), AVG(score), MAX(score), MIN(score) FROM user",
                selectStatement.toSQL()
        );
    }

    @Test
    public void testParseCountStar() throws Exception {
        lexerService = new LexerService();
        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                new ParserService(lexerService.tokenize(
                        "SELECT COUNT(*) FROM user;"
                )).parse()
        );

        AggregateFunction count = assertInstanceOf(
                AggregateFunction.class, selectStatement.getSelectList().get(0));
        assertEquals("COUNT", count.getFunctionType());
        assertEquals("*", count.getIdentifier());
    }

    @Test
    public void testParseGroupByAfterWhereBeforeOrderBy() throws Exception {
        lexerService = new LexerService();
        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                new ParserService(lexerService.tokenize(
                        "SELECT age, COUNT(age) FROM user " +
                                "WHERE age >= 18 GROUP BY age, score ORDER BY age DESC;"
                )).parse()
        );

        assertEquals("age >= 18", selectStatement.getWhereClause().orElseThrow());
        assertEquals("age, score", selectStatement.getGroupByClause().orElseThrow());
        assertEquals("age DESC", selectStatement.getOrderByClause().orElseThrow());
        assertEquals(
                "SELECT age, COUNT(age) FROM user WHERE age >= 18 " +
                        "GROUP BY age, score ORDER BY age DESC",
                selectStatement.toSQL()
        );
    }

    @Test
    public void testParseGroupByWithoutWhereOrAggregate() throws Exception {
        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                new ParserService(new LexerService().tokenize(
                        "SELECT age FROM user GROUP BY age;"
                )).parse()
        );

        assertEquals("age", selectStatement.getGroupByClause().orElseThrow());
        assertTrue(selectStatement.getWhereClause().isEmpty());
        assertTrue(selectStatement.getOrderByClause().isEmpty());
    }

    @Test
    public void testParseMixedCaseSql() throws Exception {
        lexerService = new LexerService();
        parserService = new ParserService(
                lexerService.tokenize("sElEcT USERNAME fRoM USER wHeRe USER_ID=1;")
        );

        SelectStatement selectStatement = assertInstanceOf(
                SelectStatement.class,
                parserService.parse()
        );

        assertEquals(List.of("USERNAME"), selectStatement.getSelectList());
        assertEquals("USER", selectStatement.getTableName());
        assertEquals("USER_ID = 1", selectStatement.getWhereClause().orElseThrow());
    }

}
