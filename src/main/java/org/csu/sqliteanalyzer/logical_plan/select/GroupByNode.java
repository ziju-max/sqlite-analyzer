package org.csu.sqliteanalyzer.logical_plan.select;

import org.csu.sqliteanalyzer.ast.select.GroupByClause;
import org.csu.sqliteanalyzer.logical_plan.TreeRootNode;

import java.util.List;
import java.util.Objects;

/**
 * GROUP BY execution plan node.
 */
public class GroupByNode extends TreeRootNode {
    private final String groupInfo;

    public GroupByNode(String groupInfo, TreeRootNode child) {
        super(List.of(Objects.requireNonNull(child, "group by child must not be null")));
        this.groupInfo = requireText(groupInfo);
    }

    public GroupByNode(GroupByClause groupByClause, TreeRootNode child) {
        this(Objects.requireNonNull(groupByClause, "group by clause must not be null")
                        .getGroupInfo(),
                child);
    }

    public String getGroupInfo() {
        return groupInfo;
    }

    public String getGroupColumns() {
        return groupInfo;
    }

    @Override
    public String getNodeType() {
        return "GROUP BY";
    }

    @Override
    public String toString() {
        return String.format("GroupBy(groupInfo=%s)", groupInfo);
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "group info must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("group info must not be blank");
        }
        return value;
    }
}
