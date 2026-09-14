package org.csu.sqliteanalyzer.analyzer.ast.select;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate function information in a SELECT list.
 */
public class AggregateFunction {
    private static final Set<String> SUPPORTED_FUNCTIONS = Set.of(
            "COUNT", "SUM", "AVG", "MAX", "MIN"
    );

    private final String functionType;
    private final String identifier;

    public AggregateFunction(String functionType, String identifier) {
        this.functionType = normalizeFunctionType(functionType);
        this.identifier = requireText(identifier, "aggregate identifier");
    }

    public String getFunctionType() {
        return functionType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getFunctionName() {
        return functionType;
    }

    public String getColumnName() {
        return identifier;
    }

    @Override
    public String toString() {
        return functionType + "(" + identifier + ")";
    }

    private static String normalizeFunctionType(String value) {
        String functionType = requireText(value, "aggregate function type")
                .toUpperCase(Locale.ROOT);
        if (!SUPPORTED_FUNCTIONS.contains(functionType)) {
            throw new IllegalArgumentException(
                    "Unsupported aggregate function '" + value + "'"
            );
        }
        return functionType;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
