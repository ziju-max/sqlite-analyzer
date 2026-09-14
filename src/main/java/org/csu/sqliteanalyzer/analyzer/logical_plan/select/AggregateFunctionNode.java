package org.csu.sqliteanalyzer.analyzer.logical_plan.select;

import org.csu.sqliteanalyzer.analyzer.ast.select.AggregateFunction;
import org.csu.sqliteanalyzer.analyzer.logical_plan.TreeRootNode;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate function execution plan node.
 */
public class AggregateFunctionNode extends TreeRootNode {
    private final List<AggregateFunction> aggregateFunctions;

    public AggregateFunctionNode(AggregateFunction aggregateFunction, TreeRootNode child) {
        this(List.of(aggregateFunction), child);
    }

    public AggregateFunctionNode(
            List<AggregateFunction> aggregateFunctions,
            TreeRootNode child
    ) {
        super(List.of(Objects.requireNonNull(child, "aggregate child must not be null")));
        if (aggregateFunctions == null || aggregateFunctions.isEmpty()) {
            throw new IllegalArgumentException("aggregate functions must not be empty");
        }
        this.aggregateFunctions = List.copyOf(aggregateFunctions);
    }

    public AggregateFunctionNode(
            String functionType,
            String identifier,
            TreeRootNode child
    ) {
        this(new AggregateFunction(functionType, identifier), child);
    }

    public List<AggregateFunction> getAggregateFunctions() {
        return aggregateFunctions;
    }

    public AggregateFunction getAggregateFunction() {
        return aggregateFunctions.get(0);
    }

    public String getFunctionType() {
        return getAggregateFunction().getFunctionType();
    }

    public String getIdentifier() {
        return getAggregateFunction().getIdentifier();
    }

    public String getFunctionName() {
        return getFunctionType();
    }

    public String getColumnName() {
        return getIdentifier();
    }

    @Override
    public String getNodeType() {
        return "AGGREGATE";
    }

    @Override
    public String toString() {
        if (aggregateFunctions.size() == 1) {
            AggregateFunction function = getAggregateFunction();
            return String.format(
                    "AggregateFunction(functionType=%s, identifier=%s)",
                    function.getFunctionType(),
                    function.getIdentifier()
            );
        }
        return "AggregateFunctions(functions=" + aggregateFunctions + ")";
    }
}
