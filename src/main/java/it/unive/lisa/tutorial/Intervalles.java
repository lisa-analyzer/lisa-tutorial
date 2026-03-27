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

public class Intervalles implements BaseNonRelationalValueDomain<Intervalles> {
    private IntOrInf left, right;
    private final static Intervalles TOP, BOTTOM;

    static {
        TOP = new Intervalles(new IntOrInf(false), new IntOrInf(true));
        BOTTOM = new Intervalles(new IntOrInf(1), new IntOrInf(-1));
    }

    public Intervalles(int value) {
        this.left = new IntOrInf(value);
        this.right = new IntOrInf(value);
    }

    private Intervalles(IntOrInf left, IntOrInf right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Intervalles wideningAux(Intervalles other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if (this.isTop() || other.isTop())
            return this.top();
        IntOrInf left = min(this.left, other.left),
                right = max(this.right, other.right);
        if((! other.right.isPlusInf()) && (! this.right.isPlusInf()) &&
                other.right.value>this.right.value)
            right = new IntOrInf(true);
        if((! other.left.isMinusInf()) && (! this.left.isMinusInf()) &&
                this.left.value<other.left.value)
            left = new IntOrInf(false);
        return new Intervalles(left, right);
    }

    @Override
    public Intervalles lubAux(Intervalles other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if (this.isTop() || other.isTop())
            return this.top();
        return new Intervalles(min(this.left, other.left), max(this.right, other.right));
    }

    private static IntOrInf min(IntOrInf left, IntOrInf right) {
        if(left.isMinusInf() || right.isMinusInf())
            return new IntOrInf(false);
        if(left.isPlusInf())
            return right;
        if(right.isPlusInf())
            return left;
        return new IntOrInf(Math.min(left.value, right.value));
    }

    private static IntOrInf max(IntOrInf left, IntOrInf right) {
        if(left.isPlusInf() || right.isPlusInf())
            return new IntOrInf(true);
        if(left.isMinusInf())
            return right;
        if(right.isMinusInf())
            return left;
        return new IntOrInf(Math.max(left.value, right.value));
    }
    @Override
    public boolean lessOrEqualAux(Intervalles other) throws SemanticException {
        return other.left.lessOrEqual(this.left) &&
                this.right.lessOrEqual(other.right);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Intervalles) {
            if(this.left.equals(((Intervalles) obj).left) &&
                    this.right.equals(((Intervalles) obj).right))
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "["+this.left.toString()+".."+this.right.toString()+"]";
    }

    @Override
    public Intervalles top() {
        return TOP;
    }

    @Override
    public Intervalles bottom() {
        return BOTTOM;
    }

    @Override
    public StructuredRepresentation representation() {
        return new StringRepresentation(this.toString());
    }

    @Override
    public Intervalles evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if(constant.getValue() instanceof Integer)
            return new Intervalles(((Integer) constant.getValue()).intValue());
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }

    @Override
    public Intervalles evalBinaryExpression(BinaryOperator operator, Intervalles left, Intervalles right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if(operator instanceof ArithmeticOperator) {
            if (left.isBottom() || right.isBottom())
                return this.bottom();
            HashSet<Integer> s = new HashSet<>();
            if (operator instanceof AdditionOperator)
                return new Intervalles(add(left.left, right.left), add(left.right, right.right));
            if(operator instanceof SubtractionOperator)
                return new Intervalles(subtract(left.left, right.right), subtract(left.right, right.left));
            //else throw new SemanticException("Unsupported operator");
        }
        return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
    }
    private static IntOrInf subtract(IntOrInf left, IntOrInf right) throws SemanticException {
        if(left.isPlusInf() && right.isPlusInf())
            throw new SemanticException("Cannot add minus and plus infinite");
        if(right.isMinusInf() && left.isMinusInf())
            throw new SemanticException("Cannot add minus and plus infinite");
        if(left.isPlusInf() && right.isMinusInf())
            return new IntOrInf(true);
        if(left.isMinusInf() && right.isPlusInf())
            return new IntOrInf(false);
        return new IntOrInf(left.value-right.value);
    }
    private static IntOrInf add(IntOrInf left, IntOrInf right) throws SemanticException {
        if(left.isPlusInf() && right.isMinusInf())
            throw new SemanticException("Cannot add minus and plus infinite");
        if(right.isPlusInf() && left.isMinusInf())
            throw new SemanticException("Cannot add minus and plus infinite");
        if(left.isPlusInf() || right.isPlusInf())
            return new IntOrInf(true);
        if(left.isMinusInf() || right.isMinusInf())
            return new IntOrInf(false);
        return new IntOrInf(left.value+right.value);
    }

    static class IntOrInf {


        Integer value;
        boolean isPlusInf;
        IntOrInf(int value) {
            this.value = Integer.valueOf(value);
            this.isPlusInf = false;
        }
        IntOrInf(boolean isPlusInf) {
            this.isPlusInf = isPlusInf;
            this.value = null;
        }
        boolean isMinusInf() {
            return value==null && !isPlusInf;
        }
        boolean isPlusInf() {
            return value==null && isPlusInf;
        }

        public boolean lessOrEqual(IntOrInf left) {
            if(this.isMinusInf() || left.isPlusInf())
                return true;
            if(this.isPlusInf() || left.isMinusInf())
                return false;
            return this.value<=left.value;
        }

        @Override
        public String toString() {
            if(this.isPlusInf())
                return "+inf";
            if(isMinusInf())
                return "-inf";
            return value.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof IntOrInf) {
                if(this.isPlusInf() && ((IntOrInf) obj).isPlusInf())
                    return true;
                if(this.isMinusInf() && ((IntOrInf) obj).isMinusInf())
                    return true;
                if(this.value!=null && ((IntOrInf) obj).value!=null)
                    return this.value.equals(((IntOrInf) obj).value);
            }
            return false;
        }
    }

}
