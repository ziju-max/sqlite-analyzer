package org.csu.sqliteanalyzer.db;

import org.csu.sqliteanalyzer.analyzer.ast.ASTNode;
import org.csu.sqliteanalyzer.analyzer.ast.create.ColumnDefinition;
import org.csu.sqliteanalyzer.analyzer.ast.create.CreateTableStatement;
import org.csu.sqliteanalyzer.analyzer.exception.LexerException;
import org.csu.sqliteanalyzer.analyzer.exception.PlanException;
import org.csu.sqliteanalyzer.analyzer.exception.SemanticException;
import org.csu.sqliteanalyzer.analyzer.exception.SyntaxException;
import org.csu.sqliteanalyzer.analyzer.logical_plan.LogicalPlan;
import org.csu.sqliteanalyzer.analyzer.services.LexerService;
import org.csu.sqliteanalyzer.analyzer.services.LogicalPlanService;
import org.csu.sqliteanalyzer.analyzer.services.LogicalPlanTextService;
import org.csu.sqliteanalyzer.analyzer.services.ParserService;
import org.csu.sqliteanalyzer.analyzer.services.SemanticService;
import org.csu.sqliteanalyzer.engine.catalog.CatalogManager;
import org.csu.sqliteanalyzer.engine.metadata.Column;
import org.csu.sqliteanalyzer.engine.metadata.DataType;
import org.csu.sqliteanalyzer.engine.metadata.TableInfo;
import org.csu.sqliteanalyzer.engine.storage.StorageEngine;
import org.csu.sqliteanalyzer.execution.ExecutionEngine;
import org.csu.sqliteanalyzer.storage.StorageSystem;
import org.csu.sqliteanalyzer.storage.buffer.BufferPool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库门面：把「编译器前端 + 存储/引擎 + 执行引擎」串成一条完整流水线。
 *
 * <p>一条 SQL 进来，依次经过：词法分析 -> 语法分析(AST) -> 语义分析 -> 执行计划生成 ->
 * 执行，最后返回各阶段的结果文本。这个类本身不实现任何算法，只负责编排各模块。</p>
 */
public class SqliteDatabase {

    private final LexerService lexer = new LexerService();
    private final SemanticService semanticService;
    private final LogicalPlanService planService = new LogicalPlanService();
    private final LogicalPlanTextService planTextService = new LogicalPlanTextService();

    private final StorageSystem storage;
    private final CatalogManager catalog;
    private final StorageEngine storageEngine;
    private final ExecutionEngine executionEngine;

    public SqliteDatabase(String dataFile) {
        this.storage = new StorageSystem(dataFile);
        this.catalog = new CatalogManager(storage);
        this.storageEngine = new StorageEngine(storage, catalog);
        this.executionEngine = new ExecutionEngine(storageEngine, catalog);
        this.semanticService = new SemanticService(buildSchemaMap());
    }

    /**
     * 执行一条 SQL，返回分阶段的结果文本（含错误信息）。
     */
    public String execute(String sql) {
        StringBuilder out = new StringBuilder("SQL> ").append(sql.trim()).append("\n");
        try {
            // 1. 词法分析
            List<Map<String, Object>> tokens = lexer.tokenize(sql);
            out.append("\n【1. 词法分析 Token 流】\n");
            for (Map<String, Object> t : tokens) {
                out.append(formatToken(t)).append('\n');
            }
            // 2. 语法分析
            ASTNode ast = new ParserService(tokens).parse();
            out.append("\n【2. 语法分析 AST】\n").append(ast).append('\n');

            // 3. 语义分析
            out.append("\n【3. 语义分析】\n");
            if (ast instanceof CreateTableStatement c) {
                if (catalog.tableExists(c.getTableName())) {
                    out.append("✗ 错误：表 ").append(c.getTableName()).append(" 已存在\n");
                    return out.toString();
                }
                out.append("✓ 通过（表不存在，可创建）\n");
            } else {
                semanticService.analyze(ast);
                out.append("✓ 通过\n");
            }

            // 4. 执行计划生成
            out.append("\n【4. 执行计划】\n");
            if (ast instanceof CreateTableStatement c) {
                out.append("CreateTable(").append(c.getTableName()).append(")\n");
            } else {
                LogicalPlan plan = planService.generate(ast);
                out.append(planTextService.toText(plan)).append('\n');
            }

            // 5. 执行
            out.append("\n【5. 执行结果】\n");
            out.append(executionEngine.execute(ast)).append('\n');

            // 建表成功后，把新表同步到编译器的语义目录里，后续语句才能通过语义检查
            if (ast instanceof CreateTableStatement c) {
                Map<String, DataType> columnTypes = new LinkedHashMap<>();
                for (ColumnDefinition cd : c.getColumnDefinitions()) {
                    columnTypes.put(cd.getName(), mapType(cd.getDataType().getName()));
                }
                semanticService.registerTable(c.getTableName(), columnTypes);
            }

            return out.toString();
        } catch (LexerException e) {
            return out.append("\n✗ 词法错误：").append(e.getMessage()).append('\n').toString();
        } catch (SyntaxException e) {
            return out.append("\n✗ 语法错误：").append(e.getMessage()).append('\n').toString();
        } catch (SemanticException e) {
            return out.append("\n✗ 语义错误：").append(e.getMessage()).append('\n').toString();
        } catch (PlanException e) {
            return out.append("\n✗ 执行计划错误：").append(e.getMessage()).append('\n').toString();
        } catch (RuntimeException e) {
            return out.append("\n✗ 执行错误：").append(e.getMessage()).append('\n').toString();
        }
    }

    /**
     * 关闭数据库，刷盘并写回数据文件。
     */
    public void shutdown() {
        storage.shutdown();
    }

    /**
     * 返回缓存统计信息（命中率、淘汰次数），用于演示和答辩。
     */
    public String stats() {
        BufferPool pool = storage.getBufferPool();
        return String.format(
                "[缓存统计] 命中 %d 次 / 未命中 %d 次 / 淘汰 %d 次，命中率 %.1f%%",
                pool.getHitCount(), pool.getMissCount(), pool.getEvictCount(),
                pool.getHitRate() * 100);
    }

    /** 把系统目录里的所有表转成编译器语义目录需要的结构（表名 -> 列名/类型）。 */
    private Map<String, Map<String, DataType>> buildSchemaMap() {
        Map<String, Map<String, DataType>> map = new LinkedHashMap<>();
        for (TableInfo t : catalog.getAllTables()) {
            Map<String, DataType> columns = new LinkedHashMap<>();
            for (Column c : t.getColumns()) {
                columns.put(c.getName(), c.getType());
            }
            map.put(t.getTableName(), columns);
        }
        return map;
    }

    private DataType mapType(String name) {
        return name.toUpperCase(Locale.ROOT).contains("INT")
                ? DataType.INT
                : DataType.VARCHAR;
    }

    private String formatToken(Map<String, Object> t) {
        return String.format("  [%s, %s, 行%s, 列%s]",
                t.get("type"), t.get("value"), t.get("line"), t.get("startColumn"));
    }
}
