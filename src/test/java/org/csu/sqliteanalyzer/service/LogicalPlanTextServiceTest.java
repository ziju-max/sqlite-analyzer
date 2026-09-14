package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.logical_plan.TreeRoot;
import org.csu.sqliteanalyzer.services.LogicalPlanService;
import org.csu.sqliteanalyzer.services.LexerService;
import org.csu.sqliteanalyzer.services.LogicalPlanTextService;
import org.csu.sqliteanalyzer.services.ParserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogicalPlanTextServiceTest {
    private final LexerService lexerService = new LexerService();
    private final LogicalPlanService logicalPlanService = new LogicalPlanService();
    private final LogicalPlanTextService logicalPlanTextService = new LogicalPlanTextService();

    @Test
    public void testOutputStructuredLogicalPlanWithComplexWhereClause() throws Exception {
        TreeRoot treeRoot = generate(
                "SELECT username FROM user " +
                        "WHERE (user_id > 1 OR grade >= 3 AND score >= 90) AND age > 18;"
        );

        assertEquals("""
                -> Project: user.username
                    -> Filter: ((user.user_id > 1) or ((user.grade >= 3) and (user.score >= 90))) and (user.age > 18)
                        -> Table scan on user""",
                logicalPlanTextService.toText(treeRoot));
        System.out.println(logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithoutWhereClause() throws Exception {
        TreeRoot treeRoot = generate("INSERT INTO user(username,age) VALUES('ziju',18);");

        System.out.println(logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithOrderByClause() throws Exception {
        TreeRoot treeRoot = generate(
                "SELECT username FROM user WHERE age >= 18 ORDER BY age DESC, username ASC;"
        );

        assertEquals("""
                -> Project: user.username
                    -> Sort: user.age DESC, user.username ASC
                        -> Filter: user.age >= 18
                            -> Table scan on user""",
                logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithAggregateFunctions() throws Exception {
        TreeRoot treeRoot = generate(
                "SELECT COUNT(age), SUM(age), AVG(age), MAX(age), MIN(age) " +
                        "FROM user WHERE age >= 18 ORDER BY age DESC;"
        );

        assertEquals("""
                -> Project: COUNT(user.age), SUM(user.age), AVG(user.age), MAX(user.age), MIN(user.age)
                    -> Sort: user.age DESC
                        -> Aggregate: COUNT(user.age), SUM(user.age), AVG(user.age), MAX(user.age), MIN(user.age)
                            -> Filter: user.age >= 18
                                -> Table scan on user""",
                logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithGroupByClause() throws Exception {
        TreeRoot treeRoot = generate(
                "SELECT age, COUNT(age) FROM user " +
                        "WHERE age >= 18 GROUP BY age, score ORDER BY age DESC;"
        );

        assertEquals("""
                -> Project: user.age, COUNT(user.age)
                    -> Sort: user.age DESC
                        -> Aggregate: COUNT(user.age)
                            -> Group by: user.age, user.score
                                -> Filter: user.age >= 18
                                    -> Table scan on user""",
                logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithGroupByWithoutAggregate() throws Exception {
        TreeRoot treeRoot = generate("SELECT age FROM user GROUP BY age;");

        assertEquals("""
                -> Project: user.age
                    -> Group by: user.age
                        -> Table scan on user""",
                logicalPlanTextService.toText(treeRoot));
    }

    @Test
    public void testGenerateReturnsSameStructuredPlan() throws Exception {
        TreeRoot treeRoot = generate("SELECT username FROM user WHERE user_id = 1;");

        assertEquals(logicalPlanTextService.toText(treeRoot),
                logicalPlanTextService.generate(treeRoot));
    }

    private TreeRoot generate(String source) throws Exception {
        ParserService parserService = new ParserService(lexerService.tokenize(source));
        ASTNode ast = parserService.parse();
        return logicalPlanService.generate(ast);
    }
}
