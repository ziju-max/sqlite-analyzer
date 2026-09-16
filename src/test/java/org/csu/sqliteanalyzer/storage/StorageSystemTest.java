package org.csu.sqliteanalyzer.storage;

import org.csu.sqliteanalyzer.storage.buffer.BufferPool;
import org.csu.sqliteanalyzer.storage.file.FileManager;
import org.csu.sqliteanalyzer.storage.page.Page;
import org.csu.sqliteanalyzer.storage.page.PageConstants;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageSystemTest {

    private static final String DB = "target/storage-test.data";

    @Test
    void persistsPagesThroughBufferPoolAndTracksHits() {
        deleteDatabase();

        StorageSystem storage = new StorageSystem(DB);
        try {
            int pageId = storage.allocatePage(PageConstants.PAGE_TYPE_DATA);
            Page page = storage.readPage(pageId);
            page.writeData(new byte[]{1, 2, 3});
            storage.writePage(page);

            storage.readPage(pageId);
            storage.readPage(pageId);

            BufferPool pool = storage.getBufferPool();
            assertTrue(pool.getMissCount() >= 1);
            assertTrue(pool.getHitCount() >= 2);
            assertTrue(pool.getHitRate() > 0);
        } finally {
            storage.shutdown();
        }

        FileManager fileManager = new FileManager(DB);
        try {
            Page restored = fileManager.readPage(1);
            assertEquals(1, restored.getRawData()[PageConstants.PAGE_HEADER_SIZE]);
            assertEquals(2, restored.getRawData()[PageConstants.PAGE_HEADER_SIZE + 1]);
            assertEquals(3, restored.getRawData()[PageConstants.PAGE_HEADER_SIZE + 2]);
        } finally {
            fileManager.close();
            deleteDatabase();
        }
    }

    @Test
    void evictsLeastRecentlyUsedPagesWhenCacheIsFull() {
        deleteDatabase();

        FileManager fileManager = new FileManager(DB);
        try {
            for (int i = 0; i < 3; i++) {
                fileManager.allocatePage(PageConstants.PAGE_TYPE_DATA);
            }

            BufferPool pool = new BufferPool(2, fileManager);
            pool.getPage(1);
            pool.getPage(2);
            pool.getPage(1);
            pool.getPage(3);

            assertEquals(1, pool.getEvictCount());
            assertEquals(3, pool.getMissCount());
            assertEquals(1, pool.getHitCount());
        } finally {
            fileManager.close();
            deleteDatabase();
        }
    }

    private void deleteDatabase() {
        new File(DB).delete();
    }
}
