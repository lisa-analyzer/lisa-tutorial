package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.operator.*;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.HashSet;
import java.util.Set;

public class InfiniteSetOfConcreteValues implements BaseNonRelationalValueDomain<InfiniteSetOfConcreteValues> {
    private static final int MAX_ELEMENTS = 10;
    private Set<Integer> values;
    private boolean isTop;
    private static InfiniteSetOfConcreteValues top, bottom;

    static {
        top = new InfiniteSetOfConcreteValues(true);
        bottom = new InfiniteSetOfConcreteValues(false);
    }

    private InfiniteSetOfConcreteValues(boolean isTop) {
        this.isTop = isTop;
    }

    private InfiniteSetOfConcreteValues(Set<Integer> values) throws SemanticException {
        this.values = values;
    }

    public InfiniteSetOfConcreteValues(int i) {
        values = new HashSet<>();
        values.add(Integer.valueOf(i));
    }


    @Override
    public InfiniteSetOfConcreteValues lubAux(InfiniteSetOfConcreteValues other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if(this.isTop() || other.isTop())
            return this.top();
        Set<Integer> s = new HashSet<>(this.values);
        s.addAll(other.values);
        return new InfiniteSetOfConcreteValues(s);

    }

    @Override
    public InfiniteSetOfConcreteValues wideningAux(InfiniteSetOfConcreteValues other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if(this.isTop() || other.isTop())
            return this.top();
        if(other.values.size() > MAX_ELEMENTS)
            return this.top();
        else return this.lubAux(other);
    }

    @Override
    public boolean lessOrEqualAux(InfiniteSetOfConcreteValues other) throws SemanticException {
        if(this.isTop())
            return other.isTop();
        return other.values.containsAll(this.values);
    }

    @Override
    public InfiniteSetOfConcreteValues top() {
        return top;
    }

    @Override
    public InfiniteSetOfConcreteValues bottom() {
        return bottom;
    }

    @Override
    public StructuredRepresentation representation() {
        if(isTop) return new StringRepresentation("T");
        if(values.isEmpty()) return new StringRepresentation("_|_");
        String value = "";
        for(Integer i : values)
            value += String.valueOf(i) + ";";
        return new StringRepresentation(value);
    }

    @Override
    public InfiniteSetOfConcreteValues evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        Object value = constant.getValue();
        if(value instanceof Integer) {
            return new InfiniteSetOfConcreteValues((Integer) value);
        }
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }

    @Override
    public InfiniteSetOfConcreteValues evalBinaryExpression(BinaryOperator operator, InfiniteSetOfConcreteValues left, InfiniteSetOfConcreteValues right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if(operator instanceof ArithmeticOperator) {
            if(left.isBottom() || right.isBottom())
                return this.bottom();
            if(left.isTop() || right.isTop())
                return this.top();
            HashSet<Integer> s = new HashSet<>();
            for(Integer lvalue : left.values)
                for(Integer rvalue : right.values) {
                    if (operator instanceof AdditionOperator)
                        s.add(lvalue + rvalue);
                    else if (operator instanceof SubtractionOperator)
                        s.add(lvalue - rvalue);
                    else if (operator instanceof MultiplicationOperator)
                        s.add(lvalue * rvalue);
                    else if (operator instanceof DivisionOperator) {
                        if(rvalue != 0)
                            s.add(lvalue / rvalue);
                    }
                    else throw new SemanticException("Unsupported operator");
                }
            return new InfiniteSetOfConcreteValues(s);
        }
        return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
    }
}
