package org.csu.sqliteanalyzer.engine.storage;

import org.csu.sqliteanalyzer.engine.catalog.CatalogManager;
import org.csu.sqliteanalyzer.engine.metadata.TableInfo;
import org.csu.sqliteanalyzer.storage.StorageSystem;
import org.csu.sqliteanalyzer.storage.page.Page;
import org.csu.sqliteanalyzer.storage.page.PageConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储引擎：负责把表数据组织成记录，落到页上，并提供插入 / 扫描 / 清空 / 重写等操作。
 *
 * <p>一张表的数据页通过页头里的 nextPageId 连成一条链表：根页 -> 下一页 -> ... 插入时追加
 * 到最后一页，放不下就分配新页接到链表末尾；扫描时顺着链表逐页逐行读出。</p>
 */
public class StorageEngine {

    private final StorageSystem storage;
    private final CatalogManager catalog;

    public StorageEngine(StorageSystem storage, CatalogManager catalog) {
        this.storage = storage;
        this.catalog = catalog;
    }

    /**
     * 向指定表插入一行（values 已经转成正确类型，与列顺序一致）。
     */
    public void insert(String tableName, List<Object> values) {
        TableInfo table = catalog.getTable(tableName);

        // 1. 序列化行数据
        byte[] rowData = RowSerializer.serializeRow(values, table.getColumns());

        // 2. 顺着 nextPageId 找到这张表的最后一页
        int currentPageId = table.getRootPageId();
        Page lastPage = storage.readPage(currentPageId);
        while (lastPage.getNextPageId() != PageConstants.INVALID_PAGE_ID) {
            currentPageId = lastPage.getNextPageId();
            lastPage = storage.readPage(currentPageId);
        }

        // 3. 尝试写入最后一页
        int offset = lastPage.writeData(rowData);

        if (offset == -1) {
            // 页空间不足：分配新页并接到链表末尾
            int newPageId = storage.allocatePage(PageConstants.PAGE_TYPE_DATA);
            Page newPage = storage.readPage(newPageId);

            lastPage.setNextPageId(newPageId);
            storage.writePage(lastPage);

            newPage.writeData(rowData);
            storage.writePage(newPage);
        } else {
            storage.writePage(lastPage);
        }
    }

    /**
     * 全表扫描，返回所有行（每行是 List&lt;Object&gt;，与列顺序一致）。
     */
    @SuppressWarnings("unchecked")
    public List<List<Object>> scanTable(String tableName) {
        TableInfo table = catalog.getTable(tableName);
        List<List<Object>> result = new ArrayList<>();

        int currentPageId = table.getRootPageId();
        while (currentPageId != PageConstants.INVALID_PAGE_ID) {
            Page page = storage.readPage(currentPageId);

            int offset = PageConstants.PAGE_HEADER_SIZE;
            while (offset < page.getFreeOffset()) {
                Object[] rowResult = RowSerializer.deserializeRow(
                        page.getRawData(), offset, table.getColumns());
                result.add((List<Object>) rowResult[0]);
                offset += (int) rowResult[1];
            }

            currentPageId = page.getNextPageId();
        }

        return result;
    }

    /**
     * 清空表：把根页重置成空白页。因为 nextPageId 被重置为无效，之前多分配出去的页自然
     * 变成"不可达"，等同于被回收（这里不回收磁盘空间，属于简化实现）。
     */
    public void truncateTable(String tableName) {
        TableInfo table = catalog.getTable(tableName);
        Page fresh = new Page(table.getRootPageId(), PageConstants.PAGE_TYPE_DATA);
        storage.writePage(fresh);
    }

    /**
     * 整表重写：先清空，再把给定的行逐条插回。用于 DELETE / UPDATE 的简化实现。
     */
    public void rewriteTable(String tableName, List<List<Object>> rows) {
        truncateTable(tableName);
        for (List<Object> row : rows) {
            insert(tableName, row);
        }
    }

    public StorageSystem getStorage() { return storage; }
    public CatalogManager getCatalog() { return catalog; }
}
