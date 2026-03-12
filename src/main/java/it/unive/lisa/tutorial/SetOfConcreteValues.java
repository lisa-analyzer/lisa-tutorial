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

import javax.naming.OperationNotSupportedException;
import java.util.HashSet;
import java.util.Set;

public class SetOfConcreteValues implements BaseNonRelationalValueDomain<SetOfConcreteValues> {
    private Set<Integer> values;
    private boolean isTop;
    private static SetOfConcreteValues top, bottom;
    private static final int MAX_N_ELEMENTS = 5;

    static {
        top = new SetOfConcreteValues(true);
        bottom = new SetOfConcreteValues(false);
    }

    private SetOfConcreteValues(boolean isTop) {
        this.isTop = isTop;
    }

    private SetOfConcreteValues(Set<Integer> values) throws SemanticException {
        if(values.size()>MAX_N_ELEMENTS)
            throw new SemanticException("Too many elements in the domain");
        this.values = values;
    }

    public SetOfConcreteValues(int i) {
        values = new HashSet<>();
        values.add(Integer.valueOf(i));
    }


    @Override
    public SetOfConcreteValues lubAux(SetOfConcreteValues other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if(this.isTop() || other.isTop())
            return this.top();
        Set<Integer> s = new HashSet<>(this.values);
        s.addAll(other.values);
        if(s.size()>MAX_N_ELEMENTS)
            return top();
        return new SetOfConcreteValues(s);

    }

    @Override
    public boolean lessOrEqualAux(SetOfConcreteValues other) throws SemanticException {
        if(this.isTop())
            return other.isTop();
        return other.values.containsAll(this.values);
    }

    @Override
    public SetOfConcreteValues top() {
        return top;
    }

    @Override
    public SetOfConcreteValues bottom() {
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
    public SetOfConcreteValues evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        Object value = constant.getValue();
        if(value instanceof Integer) {
            return new SetOfConcreteValues((Integer) value);
        }
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }

    @Override
    public SetOfConcreteValues evalBinaryExpression(BinaryOperator operator, SetOfConcreteValues left, SetOfConcreteValues right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
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
            if(s.size()>MAX_N_ELEMENTS)
                return top();
            return new SetOfConcreteValues(s);
        }
        return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
    }
}
