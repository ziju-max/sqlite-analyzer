package org.csu.sqliteanalyzer.analyzer.exception;

/**
 * Indicates that the SQL source contains an invalid lexical construct.
 */
public class LexerException extends IllegalArgumentException {

    private final int line;
    private final int column;

    public LexerException(String message, int line, int column) {
        super(String.format("%s at line %d, column %d", message, line, column));
        this.line = line;
        this.column = column;
    }

    public LexerException(String message, int line, int column, Throwable cause) {
        super(String.format("%s at line %d, column %d", message, line, column), cause);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
