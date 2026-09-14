package org.csu.sqliteanalyzer.analyzer.ast.select;

import java.util.Objects;

/**
 * GROUP BY clause information.
 */
public class GroupByClause {
    private final String groupInfo;

    public GroupByClause(String groupInfo) {
        this.groupInfo = requireText(groupInfo);
    }

    public String getGroupInfo() {
        return groupInfo;
    }

    public String getGroupColumns() {
        return groupInfo;
    }

    @Override
    public String toString() {
        return groupInfo;
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "group info must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("group info must not be blank");
        }
        return value;
    }
}
