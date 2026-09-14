package org.csu.sqliteanalyzer.analyzer.exception;

/**
 * 语义分析异常类
 * 当SQL语句违反数据库语义规则时抛出此异常
 */
public class SemanticException extends Exception {

    public SemanticException(String message) {
        super(message);
    }

    public SemanticException(String message, Throwable cause) {
        super(message, cause);
    }
}
