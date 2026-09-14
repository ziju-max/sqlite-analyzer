package org.csu.sqliteanalyzer.services;

import org.csu.sqliteanalyzer.ast.*;
import org.csu.sqliteanalyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.logical_plan.filter.FilterNode;
import org.csu.sqliteanalyzer.logical_plan.select.AggregateFunctionNode;
import org.csu.sqliteanalyzer.logical_plan.select.GroupByNode;
import org.csu.sqliteanalyzer.logical_plan.select.ProjectNode;
import org.csu.sqliteanalyzer.logical_plan.select.SortNode;
import org.csu.sqliteanalyzer.exception.LexerException;
import org.csu.sqliteanalyzer.common.SelectItem;
import org.csu.sqliteanalyzer.exception.PlanException;
import org.csu.sqliteanalyzer.logical_plan.*;
import org.csu.sqliteanalyzer.logical_plan.filter.BooleanExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL执行计划生成服务
 * 负责将语法树转换为逻辑执行计划
 */
public class LogicalPlanService {
    private final LexerService lexerService = new LexerService();

    public TreeRoot generate(ASTNode ast) throws PlanException {
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

    private TreeRoot generateSelectPlan(SelectStatement statement) throws PlanException {
        TreeRootNode child = generateTableAccessPlan(statement.getTableName(),
                statement.getWhereClause().orElse(null));
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
        return new TreeRoot(new ProjectNode(generateColumns(statement.getSelectList()), child));
    }

    private TreeRoot generateInsertPlan(InsertStatement statement) {
        ValuesNode values = new ValuesNode(statement.getValues());
        return new TreeRoot(new InsertNode(statement.getTableName(),
                statement.getColumns(), values));
    }

    private TreeRoot generateUpdatePlan(UpdateStatement statement) throws PlanException {
        TreeRootNode child = generateTableAccessPlan(statement.getTableName(),
                statement.getWhereClause().orElse(null));
        return new TreeRoot(new UpdateNode(statement.getTableName(),
                statement.getAssignments(), child));
    }

    private TreeRoot generateDeletePlan(DeleteStatement statement) throws PlanException {
        TreeRootNode child = generateTableAccessPlan(statement.getTableName(),
                statement.getWhereClause().orElse(null));
        return new TreeRoot(new DeleteNode(statement.getTableName(), child));
    }

    private TreeRootNode generateTableAccessPlan(String tableName, String whereClause) throws PlanException {
        TreeRootNode tableScan = new TableScanNode(tableName);
        if (whereClause == null || whereClause.isBlank()) {
            return tableScan;
        }
        return new FilterNode(whereClause, parseWhereExpression(whereClause), tableScan);
    }

    private BooleanExpression parseWhereExpression(String whereClause) throws PlanException {
        try {
            return new WhereClauseExpressionParser(lexerService.tokenize(whereClause)).parse();
        } catch (LexerException e) {
            throw new PlanException("Invalid WHERE clause: " + e.getMessage());
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
}
