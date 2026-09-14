package org.csu.sqliteanalyzer.analyzer.exception;

/**
 * 执行计划生成异常类
 * 当AST无法生成执行计划时抛出此异常
 */
public class PlanException extends Exception {

    public PlanException(String message) {
        super(message);
    }

    public PlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
