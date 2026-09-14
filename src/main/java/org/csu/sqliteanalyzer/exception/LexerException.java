package org.csu.sqliteanalyzer.exception;

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

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
