package org.csu.sqliteanalyzer.execution;

import org.csu.sqliteanalyzer.engine.metadata.DataType;

/**
 * 值转换工具：把编译器（解析器）产出的"原始字面量字符串"转换成存储引擎需要的 Java 值。
 *
 * <p>词法分析里，字符串字面量带引号（如 'Alice'）、数字不带引号（如 20）、NULL 是关键字。
 * 这里根据列类型把它们转成 Integer 或 String，顺便完成"类型一致性检查"。</p>
 */
public final class ValueConverter {

    private ValueConverter() {}

    /**
     * 把原始字面量转成对应列类型的值。
     *
     * @param raw  解析器给出的原始字符串，例如 "20"、"'Alice'"、"NULL"
     * @param type 目标列类型
     */
    public static Object parseLiteral(String raw, DataType type) {
        if (raw == null || raw.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (type == DataType.INT) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("类型不匹配：'" + raw + "' 不是整数");
            }
        }
        // VARCHAR：去掉首尾引号
        return stripQuotes(raw);
    }

    /** 去掉字符串字面量首尾的单引号或双引号（如果没有引号则原样返回） */
    public static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
