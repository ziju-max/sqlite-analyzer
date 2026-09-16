package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.LogicalPlan;
import org.csu.sqliteanalyzer.analyzer.services.LogicalPlanService;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.LogicalPlanTextService;
import org.csu.sqliteanalyzer.analyzer.services.ParserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogicalPlanTextServiceTest {
    private final LexerService lexerService = new LexerService();
    private final LogicalPlanService logicalPlanService = new LogicalPlanService();
    private final LogicalPlanTextService logicalPlanTextService = new LogicalPlanTextService();

    @Test
    public void testOutputStructuredLogicalPlanWithComplexWhereClause() throws Exception {
        LogicalPlan logicalPlan = generate(
                "SELECT\n" +
                        "    user.username,\n" +
                        "    home.name\n" +
                        "FROM user\n" +
                        "JOIN home\n" +
                        "    ON user.home_id = home.id\n" +
                        "WHERE (user.id >= 10 or user.age>18) and user.home='CS';"
        );
        System.out.println(logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithoutWhereClause() throws Exception {
        LogicalPlan logicalPlan = generate("INSERT INTO user(username,age) VALUES('ziju',18);");

        System.out.println(logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithOrderByClause() throws Exception {
        LogicalPlan logicalPlan = generate(
                "SELECT username FROM user WHERE age >= 18 ORDER BY age DESC, username ASC;"
        );

        assertEquals("""
                -> Project: user.username
                    -> Sort: user.age DESC, user.username ASC
                        -> Filter: user.age >= 18
                            -> Table scan on user""",
                logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithAggregateFunctions() throws Exception {
        LogicalPlan logicalPlan = generate(
                "SELECT COUNT(age), SUM(age), AVG(age), MAX(age), MIN(age) " +
                        "FROM user WHERE age >= 18 group by home ORDER BY age DESC;"
        );
        System.out.println(logicalPlanTextService.toText(logicalPlan));

//        assertEquals("""
//                -> Project: COUNT(user.age), SUM(user.age), AVG(user.age), MAX(user.age), MIN(user.age)
//                    -> Sort: user.age DESC
//                        -> Aggregate: COUNT(user.age), SUM(user.age), AVG(user.age), MAX(user.age), MIN(user.age)
//                            -> Filter: user.age >= 18
//                                -> Table scan on user""",
//                logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithGroupByClause() throws Exception {
        LogicalPlan logicalPlan = generate(
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
                logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testOutputStructuredLogicalPlanWithGroupByWithoutAggregate() throws Exception {
        LogicalPlan logicalPlan = generate("SELECT age FROM user GROUP BY age;");

        assertEquals("""
                -> Project: user.age
                    -> Group by: user.age
                        -> Table scan on user""",
                logicalPlanTextService.toText(logicalPlan));
    }

    @Test
    public void testGenerateReturnsSameStructuredPlan() throws Exception {
        //TreeRoot treeRoot = generate("SELECT username FROM user WHERE user_id = 1;");
        LogicalPlan logicalPlan = generate("select id from student join s on student.username=s.s_name;");
        System.out.println(logicalPlanTextService.toText(logicalPlan));
        System.out.println(logicalPlan.toString());

        assertEquals(logicalPlanTextService.toText(logicalPlan),
                logicalPlanTextService.generate(logicalPlan));
    }

    private LogicalPlan generate(String source) throws Exception {
        ParserService parserService = new ParserService(lexerService.tokenize(source));
        ASTNode ast = parserService.parse();
        return logicalPlanService.generate(ast);
    }
}
