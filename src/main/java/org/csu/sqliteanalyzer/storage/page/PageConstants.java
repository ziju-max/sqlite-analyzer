package org.csu.sqliteanalyzer.storage.page;

/**
 * 页相关常量定义。
 *
 * <p>页是磁盘存储的最小单位，这里采用固定 4KB 的页大小（与指导书要求一致）。
 * 每个页前面有一段"页头"用于保存页的元信息，剩余部分才是真正存数据的数据区。</p>
 */
public class PageConstants {

    /** 页大小：4KB = 4096 字节 */
    public static final int PAGE_SIZE = 4096;

    /** 页头大小：预留 32 字节存元信息（页号、页类型、空闲偏移、下一页指针） */
    public static final int PAGE_HEADER_SIZE = 32;

    /** 页中实际可用于存数据的大小 */
    public static final int PAGE_DATA_SIZE = PAGE_SIZE - PAGE_HEADER_SIZE;

    /** 普通数据页：用来存表的记录 */
    public static final byte PAGE_TYPE_DATA = 1;

    /** 系统目录页：用来存表的元数据（表名、列定义等） */
    public static final byte PAGE_TYPE_CATALOG = 2;

    /** 无效页号标记：表示"没有下一页" */
    public static final int INVALID_PAGE_ID = -1;
}
