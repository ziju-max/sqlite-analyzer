package org.csu.sqliteanalyzer.engine.storage;

import org.csu.sqliteanalyzer.engine.metadata.Column;
import org.csu.sqliteanalyzer.engine.metadata.DataType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 行序列化工具：把"逻辑上的一行数据"和"字节数组"互相转换。
 *
 * <p>格式：2 字节的行长度前缀 + 各列数据。INT 占 4 字节；VARCHAR 用 2 字节长度 + UTF-8 内容。</p>
 */
public class RowSerializer {

    /**
     * 将一行值序列化为字节数组（带长度前缀）。
     */
    public static byte[] serializeRow(List<Object> values, List<Column> columns) {
        if (values.size() != columns.size()) {
            throw new RuntimeException("列数不匹配");
        }

        // 先算总长度
        int totalLen = 0;
        for (int i = 0; i < values.size(); i++) {
            Column col = columns.get(i);
            Object val = values.get(i);
            if (col.getType() == DataType.INT) {
                totalLen += 4;
            } else {
                totalLen += 2 + ((String) val).getBytes(StandardCharsets.UTF_8).length;
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(2 + totalLen);
        buf.putShort((short) totalLen); // 行数据总长度

        for (int i = 0; i < values.size(); i++) {
            Column col = columns.get(i);
            Object val = values.get(i);
            if (col.getType() == DataType.INT) {
                buf.putInt((Integer) val);
            } else {
                byte[] strBytes = ((String) val).getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) strBytes.length);
                buf.put(strBytes);
            }
        }

        return buf.array();
    }

    /**
     * 从字节数组指定偏移量反序列化出一行数据。
     *
     * @return [行数据列表, 该行占用的总字节数]
     */
    public static Object[] deserializeRow(byte[] data, int offset, List<Column> columns) {
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset);

        short rowLen = buf.getShort(); // 行数据长度
        List<Object> values = new ArrayList<>();

        for (Column col : columns) {
            if (col.getType() == DataType.INT) {
                values.add(buf.getInt());
            } else {
                short strLen = buf.getShort();
                byte[] strBytes = new byte[strLen];
                buf.get(strBytes);
                values.add(new String(strBytes, StandardCharsets.UTF_8));
            }
        }

        int rowTotalSize = 2 + rowLen; // 2 字节长度前缀 + 数据
        return new Object[]{values, rowTotalSize};
    }
}
