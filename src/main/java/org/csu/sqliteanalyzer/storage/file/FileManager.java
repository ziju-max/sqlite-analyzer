package org.csu.sqliteanalyzer.storage.file;

import org.csu.sqliteanalyzer.storage.page.Page;
import org.csu.sqliteanalyzer.storage.page.PageConstants;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 文件管理器：负责真正把页读写到磁盘上的一个数据文件。
 *
 * <p>磁盘文件被看作一串连续的页（每个页 PAGE_SIZE 字节），第 i 个页在文件中的偏移就是
 * i * PAGE_SIZE。页号自增分配，程序重启后从文件长度恢复页号，保证页号连续、数据不丢。</p>
 */
public class FileManager {

    private final String dataFile;
    private RandomAccessFile file;
    private int nextPageId; // 下一个可分配的页号，自增

    /** 默认使用当前目录下的 db.data 作为数据文件 */
    public FileManager() {
        this("db.data");
    }

    /** 可指定数据文件路径，方便测试或同时开多个数据库实例 */
    public FileManager(String dataFile) {
        this.dataFile = dataFile;
        try {
            File f = new File(dataFile);
            boolean exists = f.exists() && f.length() > 0;
            file = new RandomAccessFile(f, "rw"); // rw：可读可写

            if (exists) {
                // 文件已存在：按文件长度算出当前已分配了多少页
                nextPageId = (int) (file.length() / PageConstants.PAGE_SIZE);
            } else {
                // 新文件：预留 0 号页给系统目录，用户数据从 1 号页开始
                nextPageId = 1;
            }
        } catch (IOException e) {
            throw new RuntimeException("文件管理器初始化失败: " + dataFile, e);
        }
    }

    /**
     * 分配一个新的空白页并立即落盘，返回页号。
     */
    public int allocatePage(byte pageType) {
        int pageId = nextPageId++;
        Page newPage = new Page(pageId, pageType);
        writePage(newPage); // 立即写入磁盘，持久化
        return pageId;
    }

    /**
     * 读取指定页号的页，返回 Page 对象。
     */
    public Page readPage(int pageId) {
        try {
            file.seek((long) pageId * PageConstants.PAGE_SIZE);
            byte[] buffer = new byte[PageConstants.PAGE_SIZE];
            int read = file.read(buffer);
            if (read != PageConstants.PAGE_SIZE) {
                throw new RuntimeException("读取页失败，页号：" + pageId);
            }
            return new Page(pageId, buffer);
        } catch (IOException e) {
            throw new RuntimeException("读取页异常", e);
        }
    }

    /**
     * 将内存中的 Page 对象写回磁盘。
     */
    public void writePage(Page page) {
        try {
            file.seek((long) page.getPageId() * PageConstants.PAGE_SIZE);
            file.write(page.getRawData());
        } catch (IOException e) {
            throw new RuntimeException("写入页异常", e);
        }
    }

    /**
     * 当前磁盘文件里一共有多少页。
     */
    public int getPageCount() {
        try {
            return (int) (file.length() / PageConstants.PAGE_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("获取页数量异常", e);
        }
    }

    /**
     * 关闭文件。
     */
    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
