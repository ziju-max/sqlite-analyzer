package org.csu.sqliteanalyzer.storage;

import org.csu.sqliteanalyzer.storage.buffer.BufferPool;
import org.csu.sqliteanalyzer.storage.file.FileManager;
import org.csu.sqliteanalyzer.storage.page.PageConstants;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存（BufferPool）有无的影响对比测试。
 *
 * <p>用同一份数据文件、同一条访问序列，分别用两种方式读页：</p>
 * <ul>
 *   <li><b>无缓存</b>：直接用 {@link FileManager#readPage(int)}，每次访问都真正读磁盘。</li>
 *   <li><b>有缓存</b>：走 {@link BufferPool#getPage(int)}，命中时直接返回内存里的页，
 *       只有未命中时才读磁盘。</li>
 * </ul>
 *
 * <p>对比指标：磁盘 IO 次数、缓存命中率、淘汰次数、总耗时。其中"磁盘 IO 次数"是最确定、
 * 最有说服力的指标——无缓存时 IO 次数 = 访问次数；有缓存时 IO 次数 = 未命中次数。</p>
 */
class BufferPoolCacheCompareTest {

    private static final String DB = "target/cache-compare.data";
    private static final int NUM_PAGES = 64;          // 数据文件里的页数
    private static final int CACHE_SIZE = 16;         // 缓存最多能装多少页
    private static final int NUM_ACCESSES = 200_000;  // 模拟的地址(页号)访问次数

    /** 一次对比的结果：磁盘读次数 / 命中率 / 淘汰次数 / 耗时(ms)。 */
    private record Result(long diskReads, double hitRate, long evictions, long elapsedMs) {}

    @Test
    void cacheReducesDiskIoAndSpeedsUpRepeatedReads() {
        deleteDatabase();
        try {
            prepareDataFile();
            printHeader();

            // 场景一：强局部性，90% 落在 8 个热页（工作集小，能装进缓存）
            Result[] locality = compare("局部性访问(90% 落在 8 个热页)", localitySequence());

            // 场景二：随机访问，64 页均匀随机（几乎无局部性，工作集远超缓存）
            Result[] random = compare("随机访问(64 页均匀随机)", randomSequence());

            printConclusion();

            // ---- 断言：验证"缓存确实带来了收益" ----
            // 1) 局部性访问时，命中率应很高（> 80%）
            assertTrue(locality[1].hitRate() > 0.8,
                    "局部性访问时命中率应 > 80%，实际 " + locality[1].hitRate());
            // 2) 局部性访问时，缓存应把磁盘 IO 减少到一半以下
            assertTrue(locality[1].diskReads() < locality[0].diskReads() / 2,
                    "有缓存应显著减少磁盘读，实际 " + locality[1].diskReads()
                            + " vs " + locality[0].diskReads());
            // 3) 随机访问的命中率应明显低于局部性访问（体现"局部性"的作用）
            assertTrue(random[1].hitRate() < locality[1].hitRate(),
                    "随机访问命中率应低于局部性访问");
        } finally {
            deleteDatabase();
        }
    }

    /** 对给定访问序列跑一次"无缓存 vs 有缓存"对比，打印结果并返回两者 Result。 */
    private Result[] compare(String label, int[] seq) {
        Result noCache = runNoCache(seq);
        Result withCache = runWithCache(seq);
        printScenario(label, noCache, withCache);
        return new Result[]{noCache, withCache};
    }

    /** 无缓存：直接用 FileManager 读页，每次访问都是一次真实磁盘读。 */
    private Result runNoCache(int[] seq) {
        FileManager fm = new FileManager(DB);
        try {
            long start = System.nanoTime();
            for (int pageId : seq) {
                fm.readPage(pageId);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new Result(seq.length, -1.0, 0, elapsedMs);
        } finally {
            fm.close();
        }
    }

    /** 有缓存：走 BufferPool，命中直接返回内存页，未命中才读磁盘。 */
    private Result runWithCache(int[] seq) {
        FileManager fm = new FileManager(DB);
        BufferPool pool = new BufferPool(CACHE_SIZE, fm);
        try {
            long start = System.nanoTime();
            for (int pageId : seq) {
                pool.getPage(pageId);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new Result(pool.getMissCount(), pool.getHitRate(),
                    pool.getEvictCount(), elapsedMs);
        } finally {
            fm.close();
        }
    }

    // ==================== 数据准备 ====================

    /** 新建数据文件并写入 NUM_PAGES 页（页号 1..64）。 */
    private void prepareDataFile() {
        FileManager fm = new FileManager(DB);
        try {
            for (int i = 0; i < NUM_PAGES; i++) {
                fm.allocatePage(PageConstants.PAGE_TYPE_DATA);
            }
        } finally {
            fm.close();
        }
    }

    private void deleteDatabase() {
        new File(DB).delete();
    }

    // ==================== 访问序列 ====================

    /** 强局部性：90% 落在 8 个热页，10% 均匀随机落到全部 64 页。 */
    private int[] localitySequence() {
        Random rnd = new Random(42);
        int[] hot = {1, 2, 3, 4, 5, 6, 7, 8};
        int[] seq = new int[NUM_ACCESSES];
        for (int i = 0; i < seq.length; i++) {
            seq[i] = rnd.nextDouble() < 0.90
                    ? hot[rnd.nextInt(hot.length)]
                    : 1 + rnd.nextInt(NUM_PAGES);
        }
        return seq;
    }

    /** 随机访问：64 页均匀随机，几乎无局部性。 */
    private int[] randomSequence() {
        Random rnd = new Random(42);
        int[] seq = new int[NUM_ACCESSES];
        for (int i = 0; i < seq.length; i++) {
            seq[i] = 1 + rnd.nextInt(NUM_PAGES);
        }
        return seq;
    }

    // ==================== 打印 ====================

    private void printHeader() {
        System.out.println("=".repeat(64));
        System.out.println("   缓存(BufferPool)有无 对 磁盘IO的影响对比");
        System.out.println("=".repeat(64));
        System.out.printf("   数据文件页数 : %d%n", NUM_PAGES);
        System.out.printf("   缓存页数     : %d%n", CACHE_SIZE);
        System.out.printf("   访问次数     : %,d%n", NUM_ACCESSES);
        System.out.println("=".repeat(64));
    }

    private void printScenario(String label, Result noCache, Result withCache) {
        double ioReduction = 1.0 - (double) withCache.diskReads() / noCache.diskReads();

        System.out.println();
        System.out.println("  [" + label + "]");
        System.out.printf("    无缓存(直接读盘)  : 磁盘IO %,d 次, 耗时 %,d ms%n",
                noCache.diskReads(), noCache.elapsedMs());
        System.out.printf("    有缓存(BufferPool) : 磁盘IO %,d 次, 命中率 %.1f%%, 淘汰 %,d 次, 耗时 %,d ms%n",
                withCache.diskReads(), withCache.hitRate() * 100,
                withCache.evictions(), withCache.elapsedMs());
        System.out.printf("    -> 磁盘IO减少 %.1f%%%n", ioReduction * 100);
    }

    private void printConclusion() {
        System.out.println();
        System.out.println("=".repeat(64));
        System.out.println("  结论:");
        System.out.println("    1) 无缓存时每次读页都要走磁盘，磁盘 IO 次数 = 访问次数。");
        System.out.println("    2) 有缓存时命中的页直接返回内存，只有未命中才读磁盘。");
        System.out.println("    3) 程序有局部性(局部性访问)时命中率高，磁盘 IO 大幅减少;");
        System.out.println("       随机访问时工作集超过缓存容量，命中率低，缓存收益很小。");
        System.out.println("    注: 随机访问场景中，当前 BufferPool 淘汰页时会写回磁盘");
        System.out.println("        (即使页面未被修改)，所以耗时可能不占优; 真实系统");
        System.out.println("        只写回脏页，不会出现这种情况。");
        System.out.println("=".repeat(64));
    }
}
