package org.csu.sqliteanalyzer.analyzer.services;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.DeleteStatement;
import org.csu.sqliteanalyzer.analyzer.ast.InsertStatement;
import org.csu.sqliteanalyzer.analyzer.ast.UpdateStatement;
import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.ast.select.JoinClause;
import org.csu.sqliteanalyzer.analyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.analyzer.logical_plan.*;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.FilterNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.AggregateFunctionNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.GroupByNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.JoinNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.ProjectNode;
import org.csu.sqliteanalyzer.analyzer.logical_plan.select.SortNode;
import org.csu.sqliteanalyzer.analyzer.exception.LexerException;
import org.csu.sqliteanalyzer.analyzer.common.SelectItem;
import org.csu.sqliteanalyzer.analyzer.exception.PlanException;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.BooleanExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL执行计划生成服务
 * 负责将语法树转换为逻辑执行计划
 */
public class LogicalPlanService {
    private final LexerService lexerService = new LexerService();

    public LogicalPlan generate(ASTNode ast) throws PlanException {
        try {
            return generateInternal(ast);
        } catch (PlanException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PlanException(
                    "Unable to generate logical plan: " + exceptionMessage(e), e);
        }
    }

    private LogicalPlan generateInternal(ASTNode ast) throws PlanException {
        if (ast == null) {
            throw new PlanException("AST node must not be null");
        }

        if (ast instanceof SelectStatement) {
            return generateSelectPlan((SelectStatement) ast);
        } else if (ast instanceof InsertStatement) {
            return generateInsertPlan((InsertStatement) ast);
        } else if (ast instanceof UpdateStatement) {
            return generateUpdatePlan((UpdateStatement) ast);
        } else if (ast instanceof DeleteStatement) {
            return generateDeletePlan((DeleteStatement) ast);
        }

        throw new PlanException(
                String.format("Unsupported AST node type '%s'", ast.getNodeType())
        );
    }

//    private LogicalPlan generateCreatePlan(CreateTableStatement statement) {
//        return new LogicalPlan(new CreatePlanNode(statement.getTableName(), statement.getColumns()));
//    }

    private LogicalPlan generateSelectPlan(SelectStatement statement) throws PlanException {
        TreeRootNode child = new TableScanNode(statement.getTableName());
        for (JoinClause joinClause : statement.getJoins()) {
            TreeRootNode rightChild = new TableScanNode(joinClause.getTableName());
            BooleanExpression expression = null;
            if (joinClause.hasCondition()) {
                expression = parseWhereExpression(joinClause.getCondition().orElseThrow());
            }
            child = new JoinNode(joinClause, expression, child, rightChild);
        }
        child = applyFilter(child, statement.getWhereClause().orElse(null));
        if (statement.getGroupByClause().isPresent()) {
            child = new GroupByNode(statement.getGroupByClause().orElseThrow(), child);
        }
        List<AggregateFunction> aggregateFunctions = statement.getSelectList().stream()
                .filter(AggregateFunction.class::isInstance)
                .map(AggregateFunction.class::cast)
                .toList();
        if (!aggregateFunctions.isEmpty()) {
            child = new AggregateFunctionNode(aggregateFunctions, child);
        }
        if (statement.getOrderByClause().isPresent()) {
            child = new SortNode(statement.getOrderByClause().orElseThrow(), child);
        }
        return new LogicalPlan(new ProjectNode(generateColumns(statement.getSelectList()), child));
    }

    private LogicalPlan generateInsertPlan(InsertStatement statement) {
        ValuesNode values = new ValuesNode(statement.getValues());
        return new LogicalPlan(new InsertNode(statement.getTableName(),
                statement.getColumns(), values));
    }

    private LogicalPlan generateUpdatePlan(UpdateStatement statement) throws PlanException {
        TreeRootNode child = generateTableAccessPlan(statement.getTableName(),
                statement.getWhereClause().orElse(null));
        return new LogicalPlan(new UpdateNode(statement.getTableName(),
                statement.getAssignments(), child));
    }

    private LogicalPlan generateDeletePlan(DeleteStatement statement) throws PlanException {
        TreeRootNode child = generateTableAccessPlan(statement.getTableName(),
                statement.getWhereClause().orElse(null));
        return new LogicalPlan(new DeleteNode(statement.getTableName(), child));
    }

    private TreeRootNode generateTableAccessPlan(String tableName, String whereClause) throws PlanException {
        TreeRootNode tableScan = new TableScanNode(tableName);
        return applyFilter(tableScan, whereClause);
    }

    private TreeRootNode applyFilter(TreeRootNode child, String whereClause) throws PlanException {
        if (whereClause == null || whereClause.isBlank()) {
            return child;
        }
        return new FilterNode(whereClause, parseWhereExpression(whereClause), child);
    }

    private BooleanExpression parseWhereExpression(String whereClause) throws PlanException {
        try {
            return new WhereClauseExpressionParser(lexerService.tokenize(whereClause)).parse();
        } catch (LexerException e) {
            throw new PlanException("Invalid WHERE clause: " + e.getMessage(), e);
        } catch (PlanException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PlanException(
                    "Unable to parse WHERE clause: " + exceptionMessage(e), e);
        }
    }

    private List<String> generateColumns(List<Object> selectList) throws PlanException {
        List<String> columns = new ArrayList<>();

        for (Object item : selectList) {
            if (item instanceof String) {
                columns.add((String) item);
            } else if (item instanceof SelectItem) {
                columns.add(item.toString());
            } else if (item instanceof AggregateFunction) {
                columns.add(item.toString());
            } else {
                throw new PlanException("Unsupported SELECT list item");
            }
        }

        return columns;
    }

    private String exceptionMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
