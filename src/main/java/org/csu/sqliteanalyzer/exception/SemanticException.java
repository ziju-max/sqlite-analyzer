package org.csu.sqliteanalyzer.exception;

/**
 * 语义分析异常类
 * 当SQL语句违反数据库语义规则时抛出此异常
 */
public class SemanticException extends Exception {

    public SemanticException(String message) {
        super(message);
    }
}
