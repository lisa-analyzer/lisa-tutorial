package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.symbolic.value.Identifier;

public class IntervalNotEqualProductDomain extends ValueCartesianProduct<
        ValueEnvironment<Intervalles>,
        NotEqualsDomain> {
    public IntervalNotEqualProductDomain(ValueEnvironment<Intervalles> left, NotEqualsDomain right) {
        super(left, right);
    }

    @Override
    public IntervalNotEqualProductDomain mk(ValueEnvironment<Intervalles> left, NotEqualsDomain right) {
        return new IntervalNotEqualProductDomain(left, right).reduce();
    }

    private IntervalNotEqualProductDomain reduce() {
        ValueEnvironment<Intervalles> newLeft = this.left;
        NotEqualsDomain newRight = this.right;

        for(Identifier variable : newLeft.getKeys()) {
            Intervalles value = newLeft.getState(variable);
            if(value.getMin()!=null && value.getMax()!=null && value.getMin()==value.getMax()) {
                for(Identifier variable2: newRight.getKeys()) {
                    if(variable.equals(variable2)) {
                        for(Identifier variable3: newRight.getState(variable2)) {
                            Intervalles otherValue = newLeft.getState(variable3);
                            if(otherValue.getMin()!=null)
                                    if(otherValue.getMin().intValue()==value.getMin().intValue()){
                                Intervalles newValue = new Intervalles(new Intervalles.IntOrInf(otherValue.getMin()+1),
                                        otherValue.getMax() == null ?
                                        Intervalles.IntOrInf.infinite :
                                        new Intervalles.IntOrInf(otherValue.getMax()));
                                newLeft = newLeft.putState(variable3, newValue);
                            }
                        }
                    }
                }

            }
        }

        return new IntervalNotEqualProductDomain(newLeft, newRight);

    }
}
