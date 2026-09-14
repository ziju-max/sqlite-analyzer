package org.csu.sqliteanalyzer.analyzer.exception;

/**
 * SQL语法异常类
 * 当解析器遇到语法错误时抛出此异常
 */
public class SyntaxException extends Exception {
    private int line;
    private int column;
    private String sqlText;

    public SyntaxException(String message) {
        super(message);
    }

    public SyntaxException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public SyntaxException(String message, int line, int column, String sqlText) {
        super(message);
        this.line = line;
        this.column = column;
        this.sqlText = sqlText;
    }

    public SyntaxException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyntaxException(String message, int line, int column, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getSqlText() {
        return sqlText;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(
                super.getMessage() == null ? getClass().getSimpleName() : super.getMessage());
        if (line > 0 && column > 0) {
            sb.append(String.format(" at line %d, column %d", line, column));
        }
        if (sqlText != null) {
            sb.append("\nSQL: ").append(sqlText);
        }
        return sb.toString();
    }
}
