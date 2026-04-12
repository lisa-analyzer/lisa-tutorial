package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

public class ExtendedSignsAndTVPICartesian extends CartesianProduct<ExtendedSignsAndTVPICartesian,TwoVariablesPerInequality,ValueEnvironment<ExtendedSigns>,ValueExpression,Identifier>
        implements ValueDomain<ExtendedSignsAndTVPICartesian>
{
    public ExtendedSignsAndTVPICartesian(TwoVariablesPerInequality left, ValueEnvironment<ExtendedSigns> right) {
        super(left, right);
    }
    @Override
    public boolean knowsIdentifier(Identifier id) {
        return left.knowsIdentifier(id) || right.knowsIdentifier(id);
    }

    @Override
    public ExtendedSignsAndTVPICartesian mk(TwoVariablesPerInequality left, ValueEnvironment<ExtendedSigns> right) {
        return new ExtendedSignsAndTVPICartesian(left, right);
    }

}
