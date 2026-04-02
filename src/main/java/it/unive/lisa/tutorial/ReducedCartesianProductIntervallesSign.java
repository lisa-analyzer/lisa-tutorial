package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.symbolic.value.Identifier;

public class ReducedCartesianProductIntervallesSign extends ValueCartesianProduct<ValueEnvironment<Signs>, ValueEnvironment<Intervalles>> {

    public ReducedCartesianProductIntervallesSign(ValueEnvironment<Signs> left, ValueEnvironment<Intervalles> right) {
        super(left, right);
    }

    @Override
    public ReducedCartesianProductIntervallesSign mk(ValueEnvironment<Signs> left, ValueEnvironment<Intervalles> right) {
        ReducedCartesianProductIntervallesSign res = new ReducedCartesianProductIntervallesSign(left, right);
        return res.reduce();
    }

    private ReducedCartesianProductIntervallesSign reduce() {
        ValueEnvironment<Signs> left = this.left;
        ValueEnvironment<Intervalles> right = this.right;
        for(Identifier id : right.getKeys()) {
            Intervalles intv = right.getState(id);
            if( ! intv.getLeft().isMinusInf() &&
                    intv.getLeft().value>0) {
                left = left.putState(id, Signs.POSITIVE);
            }
            else if( ! intv.getRight().isPlusInf() &&
                    intv.getRight().value<0) {
                left = left.putState(id, Signs.NEGATIVE);
            }
        }
        for(Identifier id : left.getKeys()) {
            Signs sign = left.getState(id);
            if(sign.equals(Signs.NEGATIVE)) {
                Intervalles intv = right.getState(id);
                if(intv.getRight().isPlusInf() || intv.getRight().value >= 0) {
                    right = right.putState(id, new Intervalles(intv.getLeft(), new Intervalles.IntOrInf(-1)));
                }
            }
        }
        return new ReducedCartesianProductIntervallesSign(left, right);
    }
}
