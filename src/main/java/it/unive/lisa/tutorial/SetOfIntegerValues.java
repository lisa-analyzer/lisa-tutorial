package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SetOfIntegerValues implements BaseNonRelationalValueDomain<SetOfIntegerValues> {
    private Set<Integer> values;

    private SetOfIntegerValues(Set<Integer> values) {
        this.values = values;
    }

    private SetOfIntegerValues(int value) {
        this.values = new HashSet<>();
        this.values.add(value);
    }

    @Override
    public SetOfIntegerValues lubAux(SetOfIntegerValues setOfIntegerValues) throws SemanticException {
        HashSet<Integer> newValues = new HashSet<>(this.values);
        newValues.addAll(setOfIntegerValues.values);
        return new SetOfIntegerValues(newValues);
    }

    @Override
    public boolean lessOrEqualAux(SetOfIntegerValues setOfIntegerValues) throws SemanticException {
        return false;
    }

    public static final SetOfIntegerValues TOP = new SetOfIntegerValues(null);

    @Override
    public SetOfIntegerValues top() {
        return TOP;
        /*HashSet<Integer> result = new HashSet<>();
        for(int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
            result.add(i);
        }
        return new SetOfIntegerValues(result);*/
    }

    private static final SetOfIntegerValues BOTTOM = new SetOfIntegerValues(new HashSet<>());

    @Override
    public SetOfIntegerValues bottom() {
        return BOTTOM;
    }


    @Override
    public StructuredRepresentation representation() {
        if (this.isBottom())
            return Lattice.bottomRepresentation();
        if (this.isTop())
            return Lattice.topRepresentation();
        return new StringRepresentation(Arrays.toString(values.toArray()));
    }


    @Override
    public SetOfIntegerValues evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (constant.getValue() instanceof Integer)
            return new SetOfIntegerValues((Integer) constant.getValue());
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }


    @Override
    public SetOfIntegerValues evalBinaryExpression(BinaryOperator operator, SetOfIntegerValues left, SetOfIntegerValues right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (operator instanceof AdditionOperator) {
            if (left.isTop() || right.isTop())
                return top();
            if (left.isBottom() || right.isBottom())
                return bottom();
            HashSet<Integer> newValues = new HashSet<>();
            for (Integer i : left.values)
                for (Integer j : right.values)
                    newValues.add(i + j);
            return new SetOfIntegerValues(newValues);
        }
        return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
    }
}