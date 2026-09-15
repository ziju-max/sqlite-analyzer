package org.csu.sqliteanalyzer.engine.metadata;

/**
 * 存储引擎支持的列数据类型，对应 SQL 里的 INT 和 VARCHAR。
 */
public enum DataType {
    /** 整数，占 4 字节 */
    INT,
    /** 变长字符串，长度前缀用 2 字节保存（最多约 32KB，足够演示用） */
    VARCHAR
}
