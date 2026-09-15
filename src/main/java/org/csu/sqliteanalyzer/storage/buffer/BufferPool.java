package org.csu.sqliteanalyzer.storage.buffer;

import org.csu.sqliteanalyzer.storage.file.FileManager;
import org.csu.sqliteanalyzer.storage.page.Page;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页缓存（Buffer Pool）：在内存里缓存最近用过的页，减少磁盘 IO。
 *
 * <p>核心是 {@link LinkedHashMap}，构造时 accessOrder=true，访问某个元素时会自动把它移到
 * 链表末尾，最久未访问的在头部；再配合 removeEldestEntry，缓存满了就淘汰最久未访问的页，
 * 从而实现了 LRU（最近最少使用）替换策略。</p>
 */
public class BufferPool {

    private final int maxPages;            // 缓存最大页数
    private final FileManager fileManager;

    // accessOrder=true：按访问顺序排序，实现 LRU
    private final LinkedHashMap<Integer, Page> cache;

    // 统计信息（命中/未命中/淘汰次数，答辩展示加分项）
    private long hitCount;
    private long missCount;
    private long evictCount;

    public BufferPool(int maxPages, FileManager fileManager) {
        this.maxPages = maxPages;
        this.fileManager = fileManager;
        this.hitCount = 0;
        this.missCount = 0;
        this.evictCount = 0;

        this.cache = new LinkedHashMap<>(maxPages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Page> eldest) {
                // 缓存元素超过上限时，淘汰最久未访问的页
                if (size() > maxPages) {
                    evictPage(eldest.getValue());
                    evictCount++;
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * 获取页：先查缓存，命中直接返回；未命中则从磁盘加载并放入缓存。
     */
    public Page getPage(int pageId) {
        if (cache.containsKey(pageId)) {
            hitCount++;
            return cache.get(pageId);
        }

        missCount++;
        Page page = fileManager.readPage(pageId);
        cache.put(pageId, page); // 满了会自动触发 LRU 淘汰
        return page;
    }

    /**
     * 更新缓存中的页（不存在则加入）。
     */
    public void updatePage(Page page) {
        cache.put(page.getPageId(), page);
    }

    /**
     * 强制把指定页刷回磁盘。
     */
    public void flushPage(int pageId) {
        Page page = cache.get(pageId);
        if (page != null) {
            fileManager.writePage(page);
        }
    }

    /**
     * 淘汰页：淘汰前先写回磁盘，保证修改不丢。
     */
    private void evictPage(Page page) {
        fileManager.writePage(page);
        System.out.println("[缓存淘汰] 页号 " + page.getPageId() + " 被 LRU 淘汰，已刷回磁盘");
    }

    /**
     * 强制刷盘所有缓存页，对应数据库里的 Checkpoint（关闭前调用）。
     */
    public void flushAll() {
        for (Page page : cache.values()) {
            fileManager.writePage(page);
        }
    }

    /** 缓存命中率 */
    public double getHitRate() {
        long total = hitCount + missCount;
        if (total == 0) return 0.0;
        return (double) hitCount / total;
    }

    // ========== 统计信息 getter ==========
    public long getHitCount() { return hitCount; }
    public long getMissCount() { return missCount; }
    public long getEvictCount() { return evictCount; }
}
