package org.csu.sqliteanalyzer.execution;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.DeleteStatement;
import org.csu.sqliteanalyzer.analyzer.ast.InsertStatement;
import org.csu.sqliteanalyzer.analyzer.ast.UpdateStatement;
import org.csu.sqliteanalyzer.analyzer.ast.create.ColumnDefinition;
import org.csu.sqliteanalyzer.analyzer.ast.create.CreateTableStatement;
import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.ast.select.SelectStatement;
import org.csu.sqliteanalyzer.analyzer.common.Assignment;
import org.csu.sqliteanalyzer.analyzer.common.SelectItem;
import org.csu.sqliteanalyzer.analyzer.exception.PlanException;
import org.csu.sqliteanalyzer.analyzer.logical_plan.filter.BooleanExpression;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.WhereClauseExpressionParser;
import org.csu.sqliteanalyzer.engine.catalog.CatalogManager;
import org.csu.sqliteanalyzer.engine.metadata.Column;
import org.csu.sqliteanalyzer.engine.metadata.DataType;
import org.csu.sqliteanalyzer.engine.metadata.TableInfo;
import org.csu.sqliteanalyzer.engine.storage.StorageEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 执行引擎：把编译器生成的 AST 真正执行到存储引擎上，实现各执行算子。
 *
 * <p>对应指导书里的 CreateTable / Insert / SeqScan / Filter / Project / Delete / Update 算子。
 * 简化说明：DELETE 和 UPDATE 采用"整表重写"——扫描出保留行后清空表再写回；JOIN 暂不执行。</p>
 */
public class ExecutionEngine {

    private final StorageEngine storage;
    private final CatalogManager catalog;
    private final LexerService lexer = new LexerService();
    private final WhereEvaluator evaluator = new WhereEvaluator();

    public ExecutionEngine(StorageEngine storage, CatalogManager catalog) {
        this.storage = storage;
        this.catalog = catalog;
    }

    /**
     * 执行一条语句的 AST，返回给用户看的结果文本。
     */
    public String execute(ASTNode ast) {
        if (ast instanceof CreateTableStatement c) {
            return executeCreate(c);
        }
        if (ast instanceof InsertStatement i) {
            return executeInsert(i);
        }
        if (ast instanceof SelectStatement s) {
            return executeSelect(s);
        }
        if (ast instanceof DeleteStatement d) {
            return executeDelete(d);
        }
        if (ast instanceof UpdateStatement u) {
            return executeUpdate(u);
        }
        throw new RuntimeException("暂不支持执行该语句类型: " + ast.getNodeType());
    }

    // ========== CreateTable 算子 ==========

    private String executeCreate(CreateTableStatement c) {
        List<Column> columns = new ArrayList<>();
        for (ColumnDefinition cd : c.getColumnDefinitions()) {
            columns.add(new Column(cd.getName(), mapType(cd.getDataType().getName())));
        }
        catalog.createTable(c.getTableName(), columns);
        return "OK，已创建表 " + c.getTableName();
    }

    // ========== Insert 算子 ==========

    private String executeInsert(InsertStatement ins) {
        TableInfo table = catalog.getTable(ins.getTableName());
        List<Object> row = buildRowValues(table, ins.getColumns(), ins.getValues());
        storage.insert(ins.getTableName(), row);
        return "OK，插入 1 行";
    }

    // ========== Select 算子（SeqScan + Filter + Project + Sort + Aggregate） ==========

    private String executeSelect(SelectStatement s) {
        if (s.getJoins() != null && !s.getJoins().isEmpty()) {
            throw new RuntimeException("JOIN 查询暂不支持执行（编译器已能解析并生成 JOIN 计划）");
        }

        TableInfo table = catalog.getTable(s.getTableName());
        List<Column> cols = table.getColumns();
        List<List<Object>> rows = storage.scanTable(s.getTableName());

        // 1. Filter：按 WHERE 条件过滤
        String where = s.getWhereClause().orElse(null);
        if (where != null && !where.isBlank()) {
            BooleanExpression expr = parseWhere(where);
            rows = rows.stream().filter(r -> evaluator.evaluate(expr, cols, r)).toList();
        }

        // 2. Project + Aggregate
        List<String> headers = new ArrayList<>();
        List<List<Object>> data;
        boolean hasAgg = s.getSelectList().stream().anyMatch(x -> x instanceof AggregateFunction);
        if (hasAgg) {
            data = computeAggregate(cols, rows, s.getSelectList(),
                    s.getGroupByClause().orElse(null), headers);
        } else {
            data = project(rows, cols, s.getSelectList(), headers);
        }

        // 3. Sort：按 ORDER BY 排序
        String orderBy = s.getOrderByClause().orElse(null);
        if (orderBy != null && !orderBy.isBlank() && !data.isEmpty()) {
            sort(data, headers, orderBy);
        }

        return formatTable(headers, data);
    }

    // ========== Delete 算子 ==========

    private String executeDelete(DeleteStatement d) {
        TableInfo table = catalog.getTable(d.getTableName());
        List<Column> cols = table.getColumns();
        List<List<Object>> rows = storage.scanTable(d.getTableName());

        String where = d.getWhereClause().orElse(null);
        BooleanExpression expr = (where == null || where.isBlank()) ? null : parseWhere(where);

        List<List<Object>> survivors = new ArrayList<>();
        int removed = 0;
        for (List<Object> row : rows) {
            if (expr != null && evaluator.evaluate(expr, cols, row)) {
                removed++;
            } else {
                survivors.add(row);
            }
        }
        storage.rewriteTable(d.getTableName(), survivors);
        return "OK，删除 " + removed + " 行";
    }

    // ========== Update 算子 ==========

    private String executeUpdate(UpdateStatement u) {
        TableInfo table = catalog.getTable(u.getTableName());
        List<Column> cols = table.getColumns();
        List<List<Object>> rows = storage.scanTable(u.getTableName());

        String where = u.getWhereClause().orElse(null);
        BooleanExpression expr = (where == null || where.isBlank()) ? null : parseWhere(where);

        // 预处理赋值：列下标 -> 转换后的新值
        Map<Integer, Object> updates = new LinkedHashMap<>();
        for (Assignment a : u.getAssignments()) {
            int idx = indexOf(cols, a.getColumnName());
            if (idx < 0) {
                throw new RuntimeException("列不存在: " + a.getColumnName());
            }
            updates.put(idx, ValueConverter.parseLiteral(a.getValue(), cols.get(idx).getType()));
        }

        int affected = 0;
        List<List<Object>> newRows = new ArrayList<>();
        for (List<Object> row : rows) {
            List<Object> newRow = new ArrayList<>(row);
            if (expr == null || evaluator.evaluate(expr, cols, row)) {
                for (Map.Entry<Integer, Object> e : updates.entrySet()) {
                    newRow.set(e.getKey(), e.getValue());
                }
                affected++;
            }
            newRows.add(newRow);
        }
        storage.rewriteTable(u.getTableName(), newRows);
        return "OK，更新 " + affected + " 行";
    }

    // ========== 辅助方法 ==========

    /** 把解析器给出的列名 + 原始值，整理成与表的完整列顺序一致、且已转成类型的行。 */
    private List<Object> buildRowValues(TableInfo table, List<String> insertCols, List<String> rawValues) {
        List<Column> cols = table.getColumns();
        List<Object> row = new ArrayList<>(cols.size());

        if (insertCols == null || insertCols.isEmpty()) {
            // 没写列名：值必须按表的列顺序给全
            if (rawValues.size() != cols.size()) {
                throw new RuntimeException("INSERT 值数量 " + rawValues.size()
                        + " 与列数量 " + cols.size() + " 不一致");
            }
            for (int i = 0; i < cols.size(); i++) {
                row.add(toStoredValue(rawValues.get(i), cols.get(i).getType()));
            }
        } else {
            // 写了列名：按列名定位；没写到的列填该类型的默认值
            for (Column col : cols) {
                int idx = indexOfName(insertCols, col.getName());
                if (idx < 0) {
                    row.add(defaultValue(col.getType()));
                } else {
                    row.add(toStoredValue(rawValues.get(idx), col.getType()));
                }
            }
        }
        return row;
    }

    /** 转类型；NULL 简化为该类型的默认值（不单独存 NULL 标记）。 */
    private Object toStoredValue(String raw, DataType type) {
        Object v = ValueConverter.parseLiteral(raw, type);
        return v == null ? defaultValue(type) : v;
    }

    private Object defaultValue(DataType type) {
        return type == DataType.INT ? 0 : "";
    }

    /** 普通投影：挑出 SELECT 指定的列。 */
    private List<List<Object>> project(List<List<Object>> rows, List<Column> cols,
                                       List<Object> selectList, List<String> headers) {
        List<Integer> projIdx = new ArrayList<>();
        if (selectList.size() == 1 && selectList.get(0) instanceof String s && "*".equals(s)) {
            for (int i = 0; i < cols.size(); i++) {
                projIdx.add(i);
                headers.add(cols.get(i).getName());
            }
        } else {
            for (Object item : selectList) {
                if (item instanceof String colName) {
                    int idx = requireColumn(cols, colName);
                    projIdx.add(idx);
                    headers.add(colName);
                } else if (item instanceof SelectItem si) {
                    int idx = requireColumn(cols, si.getColumnName());
                    projIdx.add(idx);
                    headers.add(si.toString());
                } else {
                    throw new RuntimeException("不支持的 SELECT 项: " + item);
                }
            }
        }

        List<List<Object>> result = new ArrayList<>();
        for (List<Object> row : rows) {
            List<Object> proj = new ArrayList<>(projIdx.size());
            for (int idx : projIdx) {
                proj.add(row.get(idx));
            }
            result.add(proj);
        }
        return result;
    }

    /** 聚合 + GROUP BY：把行按分组列分组，每组算出聚合函数结果。 */
    private List<List<Object>> computeAggregate(List<Column> cols, List<List<Object>> rows,
                                                List<Object> selectList, String groupByClause,
                                                List<String> headers) {
        List<Integer> groupIdx = new ArrayList<>();
        if (groupByClause != null && !groupByClause.isBlank()) {
            for (String gc : groupByClause.split(",")) {
                groupIdx.add(requireColumn(cols, gc.trim()));
            }
        }

        List<Integer> plainIdx = new ArrayList<>();
        List<AggregateFunction> aggs = new ArrayList<>();
        for (Object item : selectList) {
            if (item instanceof AggregateFunction a) {
                aggs.add(a);
            } else if (item instanceof String s && !"*".equals(s)) {
                plainIdx.add(requireColumn(cols, s));
            } else if (item instanceof SelectItem si) {
                plainIdx.add(requireColumn(cols, si.getColumnName()));
            }
        }

        for (int i : plainIdx) {
            headers.add(cols.get(i).getName());
        }
        for (AggregateFunction a : aggs) {
            headers.add(a.toString());
        }

        // 分组
        Map<List<Object>, List<List<Object>>> groups = new LinkedHashMap<>();
        for (List<Object> row : rows) {
            List<Object> key = new ArrayList<>(groupIdx.size());
            for (int gi : groupIdx) {
                key.add(row.get(gi));
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        if (groups.isEmpty()) {
            groups.put(new ArrayList<>(), new ArrayList<>()); // 空表也保留一组，COUNT 能返回 0
        }

        List<List<Object>> result = new ArrayList<>();
        for (Map.Entry<List<Object>, List<List<Object>>> e : groups.entrySet()) {
            List<Object> key = e.getKey();
            List<List<Object>> groupRows = e.getValue();
            List<Object> out = new ArrayList<>();

            for (int i : plainIdx) {
                int gi = groupIdx.indexOf(i);
                out.add(gi >= 0 ? key.get(gi) : (groupRows.isEmpty() ? null : groupRows.get(0).get(i)));
            }
            for (AggregateFunction a : aggs) {
                out.add(computeAgg(a, cols, groupRows));
            }
            result.add(out);
        }
        return result;
    }

    private Object computeAgg(AggregateFunction a, List<Column> cols, List<List<Object>> rows) {
        String fn = a.getFunctionType(); // 已是大写
        String ident = a.getIdentifier();

        if ("COUNT".equals(fn)) {
            if ("*".equals(ident)) {
                return (long) rows.size();
            }
            int idx = requireColumn(cols, ident);
            long c = 0;
            for (List<Object> r : rows) {
                if (r.get(idx) != null) c++;
            }
            return c;
        }

        int idx = requireColumn(cols, ident);
        double sum = 0;
        long count = 0;
        Double max = null;
        Double min = null;
        for (List<Object> r : rows) {
            Object v = r.get(idx);
            if (v == null) continue;
            double d = (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            sum += d;
            count++;
            if (max == null || d > max) max = d;
            if (min == null || d < min) min = d;
        }

        return switch (fn) {
            case "SUM" -> count == 0 ? null : num(sum);
            case "AVG" -> count == 0 ? null : num(sum / count);
            case "MAX" -> max == null ? null : num(max);
            case "MIN" -> min == null ? null : num(min);
            default -> throw new RuntimeException("不支持的聚合函数: " + fn);
        };
    }

    /** ORDER BY 排序：解析 "col DESC, col2 ASC" 这样的字符串后对结果行排序。 */
    private void sort(List<List<Object>> data, List<String> headers, String orderBy) {
        List<Integer> sortIdx = new ArrayList<>();
        List<Boolean> desc = new ArrayList<>();
        for (String item : orderBy.split(",")) {
            String[] parts = item.trim().split("\\s+");
            String colName = parts[0];
            int idx = indexOfName(headers, colName);
            if (idx < 0) {
                throw new RuntimeException("ORDER BY 列不存在: " + colName);
            }
            sortIdx.add(idx);
            desc.add(parts.length > 1 && parts[1].equalsIgnoreCase("DESC"));
        }

        data.sort((a, b) -> {
            for (int k = 0; k < sortIdx.size(); k++) {
                int i = sortIdx.get(k);
                Object x = a.get(i);
                Object y = b.get(i);
                int cmp = compareValues(x, y);
                if (cmp != 0) {
                    return desc.get(k) ? -cmp : cmp;
                }
            }
            return 0;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == b ? 0 : (a == null ? -1 : 1);
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return ((Comparable) a).compareTo(b);
    }

    /** 解析 WHERE 子句文本为布尔表达式树（复用编译器已有的表达式解析器）。 */
    private BooleanExpression parseWhere(String where) {
        try {
            return new WhereClauseExpressionParser(lexer.tokenize(where)).parse();
        } catch (PlanException e) {
            throw new RuntimeException("WHERE 子句解析失败: " + e.getMessage(), e);
        }
    }

    /** AST 里的类型名映射到存储引擎的 DataType。 */
    private DataType mapType(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        // INT / INTEGER / TINYINT / SMALLINT / BIGINT 等都当整数
        if (upper.contains("INT")) {
            return DataType.INT;
        }
        // VARCHAR / CHAR / TEXT 等一律按字符串处理（本引擎只区分 INT 和 VARCHAR 两类）
        return DataType.VARCHAR;
    }

    private int requireColumn(List<Column> cols, String name) {
        int idx = indexOf(cols, name);
        if (idx < 0) {
            throw new RuntimeException("列不存在: " + name);
        }
        return idx;
    }

    private int indexOf(List<Column> cols, String name) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfName(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 整数结果用 long 展示，避免出现 6.0 这样的浮点尾巴。 */
    private static Object num(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return (long) v;
        }
        return v;
    }

    /** 把结果集格式化成简单的文本表格。 */
    private String formatTable(List<String> headers, List<List<Object>> data) {
        if (headers.isEmpty()) {
            return data.isEmpty() ? "(空结果集)" : data.toString();
        }
        int n = headers.size();
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = headers.get(i).length();
        }
        for (List<Object> row : data) {
            for (int i = 0; i < n; i++) {
                w[i] = Math.max(w[i], cellStr(row.get(i)).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(pad(headers.get(i), w[i])).append(i < n - 1 ? " | " : "");
        }
        sb.append('\n');
        for (int i = 0; i < n; i++) {
            sb.append("-".repeat(w[i])).append(i < n - 1 ? "-+-" : "");
        }
        sb.append('\n');
        for (List<Object> row : data) {
            for (int i = 0; i < n; i++) {
                sb.append(pad(cellStr(row.get(i)), w[i])).append(i < n - 1 ? " | " : "");
            }
            sb.append('\n');
        }
        if (data.isEmpty()) {
            sb.append("(0 行)\n");
        }
        return sb.toString();
    }

    private static String cellStr(Object o) {
        return o == null ? "NULL" : String.valueOf(o);
    }

    private static String pad(String s, int width) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) {
            b.append(' ');
        }
        return b.toString();
    }
}
