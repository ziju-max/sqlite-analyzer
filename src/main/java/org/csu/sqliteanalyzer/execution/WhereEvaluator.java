package org.csu.sqliteanalyzer.execution;

import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.AndExpr;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.ComparisonExpr;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.NotExpr;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.OrExpr;
import org.csu.sqliteanalyzer.engine.metadata.Column;
import org.csu.sqliteanalyzer.engine.metadata.DataType;

import java.util.List;

/**
 * WHERE 条件求值器：把编译阶段解析出的布尔表达式树，作用到具体的一行数据上，判断该行
 * 是否满足条件（对应执行计划里的 Filter 算子）。
 */
public class WhereEvaluator {

    /**
     * 判断一行数据是否满足某个布尔表达式。
     *
     * @param expr    编译阶段解析好的 WHERE 表达式树
     * @param columns 表的列定义（用来按列名定位列）
     * @param row     一行数据（与 columns 顺序一致）
     */
    public boolean evaluate(BooleanExpression expr, List<Column> columns, List<Object> row) {
        if (expr instanceof ComparisonExpr c) {
            return evaluateComparison(c, columns, row);
        }
        if (expr instanceof AndExpr a) {
            return evaluate(a.getLeft(), columns, row) && evaluate(a.getRight(), columns, row);
        }
        if (expr instanceof OrExpr o) {
            return evaluate(o.getLeft(), columns, row) || evaluate(o.getRight(), columns, row);
        }
        if (expr instanceof NotExpr n) {
            return !evaluate(n.getExpression(), columns, row);
        }
        throw new RuntimeException("不支持的表达式类型: " + expr.getClass().getSimpleName());
    }

    private boolean evaluateComparison(ComparisonExpr c, List<Column> columns, List<Object> row) {
        int idx = indexOf(columns, c.getLeftOperand());
        if (idx < 0) {
            throw new RuntimeException("列不存在: " + c.getLeftOperand());
        }
        Column col = columns.get(idx);
        Object left = row.get(idx);
        Object right = ValueConverter.parseLiteral(c.getRightOperand(), col.getType());

        // 简化：任一操作数为 NULL 时，比较结果为 false（不做 SQL 的三值逻辑）
        if (left == null || right == null) {
            return false;
        }

        int cmp = compare(left, right, col.getType());
        return switch (c.getOperator()) {
            case "=", "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            default -> throw new RuntimeException("不支持的比较运算符: " + c.getOperator());
        };
    }

    private int compare(Object a, Object b, DataType type) {
        if (type == DataType.INT) {
            return Integer.compare((Integer) a, (Integer) b);
        }
        return ((String) a).compareTo((String) b);
    }

    /** 按列名（忽略大小写）找列下标，找不到返回 -1 */
    private int indexOf(List<Column> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
