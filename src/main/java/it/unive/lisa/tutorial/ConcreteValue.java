package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Objects;

public class ConcreteValue implements BaseNonRelationalValueDomain<ConcreteValue> {
    private final int value;
    private final boolean isBottom, isTop;
    private static final ConcreteValue top, bottom;

    static {
        top = new ConcreteValue(true);
        bottom = new ConcreteValue(false);
    }

    public ConcreteValue(int value) {
        this.value = value;
        this.isBottom = false;
        this.isTop = false;
    }

    private ConcreteValue(boolean isTop) {
        this.value = 0;
        if(isTop) {
            this.isBottom = false;
            this.isTop = true;
        }
        else {
            this.isBottom = true;
            this.isTop = false;
        }
    }

    @Override
    public ConcreteValue lubAux(ConcreteValue concreteValue) throws SemanticException {
        if(this.isTop() || concreteValue.isTop())
            return top();
        if(this.isBottom)
            return concreteValue;
        if(concreteValue.isBottom)
            return this;
        if(this.value==concreteValue.value)
            return this;
        return top();
    }

    @Override
    public boolean lessOrEqualAux(ConcreteValue concreteValue) throws SemanticException {
        if(concreteValue.isTop)
            return true;
        if(concreteValue.isBottom)
            return this.isBottom;
        if(this.isTop)
            return false;
        if(this.isBottom)
            return true;
        return this.value == concreteValue.value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConcreteValue that)) return false;
        return value == that.value && isBottom == that.isBottom && isTop == that.isTop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, isBottom, isTop);
    }

    @Override
    public String toString() {
        if(isTop)
            return "top";
        if(isBottom)
            return "bottom";
        return String.valueOf(value);
    }

    @Override
    public ConcreteValue top() {
        return top;
    }

    @Override
    public ConcreteValue bottom() {
        return bottom;
    }

    @Override
    public StructuredRepresentation representation() {
        return new StringRepresentation(this.toString());
    }

    @Override
    public ConcreteValue evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        Object a = constant.getValue();
        if(a instanceof Integer) {
            return new ConcreteValue((Integer) a);
        }
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }

    @Override
    public ConcreteValue evalBinaryExpression(BinaryOperator operator, ConcreteValue left, ConcreteValue right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if(operator instanceof AdditionOperator) {
            if(left.isBottom() || right.isBottom())
                return this.bottom();
            if(left.isTop() || right.isTop())
                return this.top();
            return new ConcreteValue(left.value + right.value);
        }
        if(operator instanceof SubtractionOperator) {
            if(left.isBottom() || right.isBottom())
                return this.bottom();
            if(left.isTop() || right.isTop())
                return this.top();
            return new ConcreteValue(left.value - right.value);
        }
        if(operator instanceof MultiplicationOperator) {
            if(left.isBottom() || right.isBottom())
                return this.bottom();
            if(left.isTop() || right.isTop())
                return this.top();
            return new ConcreteValue(left.value * right.value);
        }
        if(operator instanceof DivisionOperator) {
            if(left.isBottom() || right.isBottom())
                return this.bottom();
            if(left.isTop() || right.isTop())
                return this.top();
            return new ConcreteValue(left.value / right.value);
        }
        return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
    }
}
