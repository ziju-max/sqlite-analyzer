package org.csu.sqliteanalyzer.engine.catalog;

import org.csu.sqliteanalyzer.engine.metadata.Column;
import org.csu.sqliteanalyzer.engine.metadata.DataType;
import org.csu.sqliteanalyzer.engine.metadata.TableInfo;
import org.csu.sqliteanalyzer.storage.StorageSystem;
import org.csu.sqliteanalyzer.storage.page.Page;
import org.csu.sqliteanalyzer.storage.page.PageConstants;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统目录（Catalog）：维护数据库的元数据（有哪些表、每张表有哪些列、列类型、根页号）。
 *
 * <p>目录本身也通过存储系统持久化，固定存到 0 号页。程序启动时从磁盘加载，建表后写回。</p>
 */
public class CatalogManager {

    private static final int CATALOG_PAGE_ID = 0; // 固定 0 号页存目录

    private final StorageSystem storage;
    private final Map<String, TableInfo> tables; // 内存中的表元数据缓存（key 统一小写）

    public CatalogManager(StorageSystem storage) {
        this.storage = storage;
        this.tables = new HashMap<>();
        loadCatalog(); // 启动时从磁盘加载目录
    }

    /**
     * 从磁盘加载系统目录。首次启动磁盘上还没有 0 号页，直接当作空目录。
     */
    private void loadCatalog() {
        if (storage.getPageCount() <= CATALOG_PAGE_ID) {
            return; // 还没有目录页，说明是全新的数据库
        }

        Page catalogPage = storage.readPage(CATALOG_PAGE_ID);
        byte[] data = catalogPage.readData(PageConstants.PAGE_HEADER_SIZE,
                catalogPage.getFreeOffset() - PageConstants.PAGE_HEADER_SIZE);

        if (data.length == 0) {
            return; // 空目录
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        int tableCount = buf.getInt();

        for (int i = 0; i < tableCount; i++) {
            String tableName = readString(buf);
            int rootPageId = buf.getInt();
            int colCount = buf.getInt();

            List<Column> columns = new ArrayList<>();
            for (int j = 0; j < colCount; j++) {
                String colName = readString(buf);
                byte typeCode = buf.get(); // 0=INT, 1=VARCHAR
                DataType type = typeCode == 0 ? DataType.INT : DataType.VARCHAR;
                columns.add(new Column(colName, type));
            }

            tables.put(tableName.toLowerCase(), new TableInfo(tableName, columns, rootPageId));
        }
    }

    /**
     * 把目录序列化后写回 0 号目录页（全量覆盖）。
     */
    private void saveCatalog() {
        Page catalogPage = new Page(CATALOG_PAGE_ID, PageConstants.PAGE_TYPE_CATALOG);

        ByteBuffer buf = ByteBuffer.allocate(PageConstants.PAGE_DATA_SIZE);
        buf.putInt(tables.size()); // 表数量

        for (TableInfo table : tables.values()) {
            writeString(buf, table.getTableName());
            buf.putInt(table.getRootPageId());
            buf.putInt(table.getColumns().size());

            for (Column col : table.getColumns()) {
                writeString(buf, col.getName());
                buf.put(col.getType() == DataType.INT ? (byte) 0 : (byte) 1);
            }
        }

        byte[] data = new byte[buf.position()];
        buf.flip();
        buf.get(data);

        if (data.length > PageConstants.PAGE_DATA_SIZE) {
            throw new RuntimeException("系统目录过大，单个目录页放不下（请减少表或列数量）");
        }

        catalogPage.writeData(data);
        storage.writePage(catalogPage);
    }

    /**
     * 创建表：分配根页，注册元数据，持久化目录。
     */
    public void createTable(String tableName, List<Column> columns) {
        if (tables.containsKey(tableName.toLowerCase())) {
            throw new RuntimeException("表已存在: " + tableName);
        }

        int rootPageId = storage.allocatePage(PageConstants.PAGE_TYPE_DATA);
        TableInfo tableInfo = new TableInfo(tableName, columns, rootPageId);
        tables.put(tableName.toLowerCase(), tableInfo);

        saveCatalog();
        System.out.println("[系统目录] 创建表: " + tableName + "，根页号: " + rootPageId);
    }

    /**
     * 根据表名获取表元数据，不存在则抛异常。
     */
    public TableInfo getTable(String tableName) {
        TableInfo table = tables.get(tableName.toLowerCase());
        if (table == null) {
            throw new RuntimeException("表不存在: " + tableName);
        }
        return table;
    }

    /**
     * 检查表是否存在。
     */
    public boolean tableExists(String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }

    /**
     * 返回所有表（用于同步编译器语义目录、展示表列表等）。
     */
    public Collection<TableInfo> getAllTables() {
        return tables.values();
    }

    // ========== 序列化辅助 ==========

    private static void writeString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    private static String readString(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
