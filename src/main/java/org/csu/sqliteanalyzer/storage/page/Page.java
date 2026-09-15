package org.csu.sqliteanalyzer.storage.page;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 页：磁盘上的一块固定大小区域。
 *
 * <p>页 = 页头 + 数据区。页头记录页号、页类型、当前空闲位置偏移、下一页页号（同一张表
 * 的多个页通过 nextPageId 连成一条链表）。数据区里紧挨着存放一条条记录。</p>
 */
public class Page {

    // ========== 页头信息 ==========
    private int pageId;          // 页号，全局唯一
    private byte pageType;       // 页类型：数据页 / 目录页
    private short freeOffset;    // 数据区空闲位置偏移量（下一条记录从这里开始写）
    private int nextPageId;      // 下一页页号（用链表组织同一张表的多个页）

    // ========== 数据区 ==========
    private byte[] data;         // 完整的 4KB 数据（页头 + 数据区）

    /**
     * 构造一个全新的空白页。
     */
    public Page(int pageId, byte pageType) {
        this.pageId = pageId;
        this.pageType = pageType;
        this.freeOffset = PageConstants.PAGE_HEADER_SIZE; // 初始空闲位置在页头末尾
        this.nextPageId = PageConstants.INVALID_PAGE_ID;
        this.data = new byte[PageConstants.PAGE_SIZE];
        writeHeader(); // 初始化时把页头信息写入字节数组
    }

    /**
     * 从磁盘读出的字节数组反序列化为 Page 对象。
     */
    public Page(int pageId, byte[] rawData) {
        this.pageId = pageId;
        this.data = Arrays.copyOf(rawData, PageConstants.PAGE_SIZE);
        readHeader();
    }

    /** 把页头信息写入字节数组（内存 -> 磁盘时用） */
    private void writeHeader() {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putInt(pageId);        // 4 字节
        buf.put(pageType);         // 1 字节
        buf.putShort(freeOffset);  // 2 字节
        buf.putInt(nextPageId);    // 4 字节
    }

    /** 从字节数组解析页头信息（磁盘 -> 内存时用） */
    private void readHeader() {
        ByteBuffer buf = ByteBuffer.wrap(data);
        this.pageId = buf.getInt();
        this.pageType = buf.get();
        this.freeOffset = buf.getShort();
        this.nextPageId = buf.getInt();
    }

    /**
     * 向页中写入一段数据，返回写入的起始偏移量；空间不足返回 -1。
     */
    public int writeData(byte[] rowData) {
        if (freeOffset + rowData.length > PageConstants.PAGE_SIZE) {
            return -1;
        }
        System.arraycopy(rowData, 0, data, freeOffset, rowData.length);
        int writePos = freeOffset;
        freeOffset += rowData.length;
        writeHeader(); // 更新页头里的空闲偏移
        return writePos;
    }

    /**
     * 从指定偏移量读取指定长度的数据。
     */
    public byte[] readData(int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    // ========== getter / setter ==========
    public int getPageId() { return pageId; }
    public byte getPageType() { return pageType; }
    public short getFreeOffset() { return freeOffset; }
    public int getNextPageId() { return nextPageId; }
    public byte[] getRawData() { return data; }

    public void setNextPageId(int nextPageId) {
        this.nextPageId = nextPageId;
        writeHeader();
    }

    /** 当前页还剩多少字节可写 */
    public int getFreeSpace() {
        return PageConstants.PAGE_SIZE - freeOffset;
    }
}
