package org.csu.sqliteanalyzer.service;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.exception.PlanException;
import org.csu.sqliteanalyzer.analyzer.logical_plan.*;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.*;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.AggregateFunctionNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.GroupByNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.ProjectNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.SortNode;
import org.csu.sqliteanalyzer.analyzer.services.LogicalPlanService;
import org.csu.sqliteanalyzer.analyzer.services.AstTreeTextService;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.ParserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class LogicalPlanServiceTest {
    private final LexerService lexerService = new LexerService();
    private final LogicalPlanService executionPlanService = new LogicalPlanService();
    private final AstTreeTextService astTreeTextService = new AstTreeTextService();

    @Test
    public void testGenerateSelectPlan() throws Exception {
        LogicalPlan logicalPlan = generate("SELECT username FROM user WHERE user_id=1;");
        System.out.println(astTreeTextService.toText(logicalPlan));

        ProjectNode project = assertInstanceOf(ProjectNode.class, logicalPlan.getRoot());
        assertEquals(List.of("username"), project.getColumns());

        FilterNode filter = assertInstanceOf(FilterNode.class, project.getChildren().get(0));
        assertEquals("user_id = 1", filter.getCondition());

        TableScanNode tableScan = assertInstanceOf(TableScanNode.class,
                filter.getChildren().get(0));
        assertEquals("user", tableScan.getTableName());
    }

    @Test
    public void testGenerateWhereClauseExpressionTree() throws Exception {
        LogicalPlan plan = generate(
                "SELECT age FROM user WHERE age > 18 AND name = 'ju' OR user_id = 92;"
        );

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        FilterNode filter = assertInstanceOf(FilterNode.class, project.getChildren().get(0));
        OrExpr orExpr = assertInstanceOf(OrExpr.class, filter.getExpression());
        AndExpr andExpr = assertInstanceOf(AndExpr.class, orExpr.getLeft());
        ComparisonExpr ageExpr = assertInstanceOf(ComparisonExpr.class, andExpr.getLeft());
        ComparisonExpr nameExpr = assertInstanceOf(ComparisonExpr.class, andExpr.getRight());
        ComparisonExpr userIdExpr = assertInstanceOf(ComparisonExpr.class, orExpr.getRight());

        assertEquals("age", ageExpr.getLeftOperand());
        assertEquals(">", ageExpr.getOperator());
        assertEquals("18", ageExpr.getRightOperand());
        assertEquals("name", nameExpr.getLeftOperand());
        assertEquals("=", nameExpr.getOperator());
        assertEquals("'ju'", nameExpr.getRightOperand());
        assertEquals("user_id", userIdExpr.getLeftOperand());
        assertEquals("=", userIdExpr.getOperator());
        assertEquals("92", userIdExpr.getRightOperand());
        assertEquals("""
                FilterPlanNode
                └── OrExpr
                    ├── AndExpr
                    │   ├── ComparisonExpr(age, >, 18)
                    │   └── ComparisonExpr(name, =, 'ju')
                    └── ComparisonExpr(user_id, =, 92)""", filter.toExpressionTreeString());
    }

    @Test
    public void testGenerateSelectPlanWithSortNode() throws Exception {
        LogicalPlan plan = generate(
                "SELECT username FROM user WHERE age >= 18 ORDER BY age DESC, username ASC;"
        );

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        SortNode sort = assertInstanceOf(SortNode.class, project.getChildren().get(0));
        assertEquals("age DESC, username ASC", sort.getSortInfo());

        FilterNode filter = assertInstanceOf(FilterNode.class, sort.getChildren().get(0));
        assertEquals("age >= 18", filter.getCondition());
        assertInstanceOf(TableScanNode.class, filter.getChildren().get(0));
    }

    @Test
    public void testGenerateSelectPlanWithAggregateFunctionNode() throws Exception {
        LogicalPlan plan = generate(
                "SELECT COUNT(age), SUM(age), AVG(age), MAX(age), MIN(age) " +
                        "FROM user WHERE age >= 18 ORDER BY age DESC;"
        );
        System.out.println(astTreeTextService.toText(plan));

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        SortNode sort = assertInstanceOf(SortNode.class, project.getChildren().get(0));
        AggregateFunctionNode aggregate = assertInstanceOf(
                AggregateFunctionNode.class, sort.getChildren().get(0));
        assertEquals(List.of(
                        "COUNT(age)", "SUM(age)", "AVG(age)", "MAX(age)", "MIN(age)"
                ),
                aggregate.getAggregateFunctions().stream()
                        .map(Object::toString)
                        .toList());

        FilterNode filter = assertInstanceOf(FilterNode.class, aggregate.getChildren().get(0));
        assertEquals("age >= 18", filter.getCondition());
        assertInstanceOf(TableScanNode.class, filter.getChildren().get(0));
    }

    @Test
    public void testGenerateSelectPlanAutomaticallyOrdersGroupAggregateAndSortNodes() throws Exception {
        LogicalPlan plan = generate(
                "SELECT age, COUNT(age) FROM user " +
                        "WHERE age >= 18 GROUP BY age, score ORDER BY age DESC;"
        );

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        SortNode sort = assertInstanceOf(SortNode.class, project.getChildren().get(0));
        AggregateFunctionNode aggregate = assertInstanceOf(
                AggregateFunctionNode.class, sort.getChildren().get(0));
        GroupByNode groupBy = assertInstanceOf(GroupByNode.class, aggregate.getChildren().get(0));

        assertEquals("age, score", groupBy.getGroupInfo());
        assertEquals("COUNT(age)", aggregate.getAggregateFunction().toString());
        FilterNode filter = assertInstanceOf(FilterNode.class, groupBy.getChildren().get(0));
        assertEquals("age >= 18", filter.getCondition());
        assertInstanceOf(TableScanNode.class, filter.getChildren().get(0));
    }

    @Test
    public void testGenerateGroupByPlanWithoutAggregate() throws Exception {
        LogicalPlan plan = generate("SELECT age FROM user GROUP BY age;");

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        GroupByNode groupBy = assertInstanceOf(GroupByNode.class, project.getChildren().get(0));
        assertEquals("age", groupBy.getGroupColumns());
        assertInstanceOf(TableScanNode.class, groupBy.getChildren().get(0));
    }

    @Test
    public void testGenerateNotExpressionTree() throws Exception {
        LogicalPlan plan = generate("SELECT age FROM user WHERE NOT age > 18 AND user_id = 92;");

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        FilterNode filter = assertInstanceOf(FilterNode.class, project.getChildren().get(0));
        BooleanExpression expression = filter.getExpression();
        AndExpr andExpr = assertInstanceOf(AndExpr.class, expression);
        NotExpr notExpr = assertInstanceOf(NotExpr.class, andExpr.getLeft());

        assertInstanceOf(ComparisonExpr.class, notExpr.getExpression());
        assertInstanceOf(ComparisonExpr.class, andExpr.getRight());
    }

    @Test
    public void testGenerateParenthesizedWhereExpressionTree() throws Exception {
        LogicalPlan plan = generate(
                "SELECT id FROM user WHERE (id > 10 OR age < 18) AND grade >= 90;"
        );

        ProjectNode project = assertInstanceOf(ProjectNode.class, plan.getRoot());
        FilterNode filter = assertInstanceOf(FilterNode.class, project.getChildren().get(0));
        AndExpr andExpr = assertInstanceOf(AndExpr.class, filter.getExpression());
        OrExpr orExpr = assertInstanceOf(OrExpr.class, andExpr.getLeft());

        ComparisonExpr idExpr = assertInstanceOf(ComparisonExpr.class, orExpr.getLeft());
        ComparisonExpr ageExpr = assertInstanceOf(ComparisonExpr.class, orExpr.getRight());
        ComparisonExpr gradeExpr = assertInstanceOf(ComparisonExpr.class, andExpr.getRight());

        assertEquals("id", idExpr.getLeftOperand());
        assertEquals(">", idExpr.getOperator());
        assertEquals("10", idExpr.getRightOperand());
        assertEquals("age", ageExpr.getLeftOperand());
        assertEquals("<", ageExpr.getOperator());
        assertEquals("18", ageExpr.getRightOperand());
        assertEquals("grade", gradeExpr.getLeftOperand());
        assertEquals(">=", gradeExpr.getOperator());
        assertEquals("90", gradeExpr.getRightOperand());
    }

    @Test
    public void testRejectsUnclosedParenthesizedWhereExpression() throws Exception {
        PlanException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        PlanException.class,
                        () -> generate("SELECT id FROM user WHERE (id > 10 OR age < 18 AND grade >= 90;")
                );

        assertEquals("Expected ')' in WHERE clause, got end of clause", exception.getMessage());
    }

    @Test
    public void testOutputPlanAsTreeText() throws Exception {
        LogicalPlan plan = generate("SELECT username FROM user WHERE (user_id>1 or grade >=3 and score>=90) and age>18;");

//        assertEquals("""
//                Project(columns=[username])
//                  Filter(condition=user_id = 1)
//                    TableScan(table=user)""",
//                logicalPlanTextService.toText(plan));
        System.out.println(astTreeTextService.toText(plan));
    }

    @Test
    public void testGenerateInsertPlan() throws Exception {
        LogicalPlan plan = generate("INSERT INTO user(user_id,username) VALUES (1,admin);");
        System.out.println(astTreeTextService.toText(plan));

        InsertNode insert = assertInstanceOf(InsertNode.class, plan.getRoot());
        assertEquals("user", insert.getTableName());
        assertEquals(List.of("user_id", "username"), insert.getColumns());

        ValuesNode values = assertInstanceOf(ValuesNode.class, insert.getChildren().get(0));
        assertEquals(List.of("1", "admin"), values.getValues());
    }

    @Test
    public void testGenerateUpdatePlan() throws Exception {
        LogicalPlan plan = generate("UPDATE user SET username=admin WHERE user_id=1 or age>18;");
        System.out.println(astTreeTextService.toText(plan));

        UpdateNode update = assertInstanceOf(UpdateNode.class, plan.getRoot());
        assertEquals("user", update.getTableName());
        assertEquals("username", update.getAssignments().get(0).getColumnName());
        assertInstanceOf(FilterNode.class, update.getChildren().get(0));
    }

    @Test
    public void testGenerateDeletePlan() throws Exception {
        LogicalPlan plan = generate("DELETE FROM user WHERE user_id=1;");
        System.out.println(astTreeTextService.toText(plan));

        DeleteNode delete = assertInstanceOf(DeleteNode.class, plan.getRoot());
        assertEquals("user", delete.getTableName());
        assertInstanceOf(FilterNode.class, delete.getChildren().get(0));
    }

    private LogicalPlan generate(String source) throws Exception {
        ParserService parserService = new ParserService(lexerService.tokenize(source));
        ASTNode ast = parserService.parse();
        return executionPlanService.generate(ast);
    }
}
