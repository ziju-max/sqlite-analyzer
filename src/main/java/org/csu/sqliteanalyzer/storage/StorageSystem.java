package org.csu.sqliteanalyzer.storage;

import org.csu.sqliteanalyzer.storage.buffer.BufferPool;
import org.csu.sqliteanalyzer.storage.file.FileManager;
import org.csu.sqliteanalyzer.storage.page.Page;

/**
 * 存储系统：对上层暴露统一的页读写接口，内部封装"文件管理 + 页缓存"。
 *
 * <p>数据库模块只需要跟 StorageSystem 打交道，不用关心页是走缓存还是走磁盘。</p>
 */
public class StorageSystem {

    private final FileManager fileManager;
    private final BufferPool bufferPool;

    /** 默认数据文件 db.data */
    public StorageSystem() {
        this("db.data");
    }

    public StorageSystem(String dataFile) {
        this.fileManager = new FileManager(dataFile);
        this.bufferPool = new BufferPool(16, fileManager); // 缓存 16 页 = 64KB
    }

    /**
     * 分配新页。
     */
    public int allocatePage(byte pageType) {
        return fileManager.allocatePage(pageType);
    }

    /**
     * 读取页（走缓存）。
     */
    public Page readPage(int pageId) {
        return bufferPool.getPage(pageId);
    }

    /**
     * 写入页：更新缓存 + 强制刷盘。
     */
    public void writePage(Page page) {
        bufferPool.updatePage(page);
        bufferPool.flushPage(page.getPageId());
    }

    /**
     * 当前磁盘上已有多少页（用于判断某个页是否已存在）。
     */
    public int getPageCount() {
        return fileManager.getPageCount();
    }

    /**
     * 关闭存储系统，刷盘所有数据。
     */
    public void shutdown() {
        bufferPool.flushAll();
        fileManager.close();
    }

    public BufferPool getBufferPool() { return bufferPool; }
}
