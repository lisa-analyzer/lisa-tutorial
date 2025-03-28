package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;

public class SignsIntervalProductDomain extends ValueCartesianProduct<
        ValueEnvironment<Signs>,
        ValueEnvironment<Intervalles>> {
    public SignsIntervalProductDomain(ValueEnvironment<Signs> left, ValueEnvironment<Intervalles> right) {
        super(left, right);
    }

    @Override
    public SignsIntervalProductDomain mk(ValueEnvironment<Signs> left, ValueEnvironment<Intervalles> right) {
        return new SignsIntervalProductDomain(left, right).reduce();
    }

    private SignsIntervalProductDomain reduce() {
        ValueEnvironment<Intervalles> newRight = this.right;
        for(Identifier variable : left.getKeys()) {
            Signs sign = left.getState(variable);
            if(sign == Signs.POSITIVE) {
                Intervalles intervalles = right.getState(variable);
                Intervalles.IntOrInf min = intervalles.getMin()==null ? new Intervalles.IntOrInf(1) : new Intervalles.IntOrInf(intervalles.getMin());
                Intervalles.IntOrInf max = intervalles.getMax()==null ? Intervalles.IntOrInf.infinite : new Intervalles.IntOrInf(intervalles.getMax());
                newRight = newRight.putState(variable, new Intervalles(Intervalles.IntOrInf.max(new Intervalles.IntOrInf(1), min), max));
            }
        }
        return new SignsIntervalProductDomain(left, newRight);
    }
}
