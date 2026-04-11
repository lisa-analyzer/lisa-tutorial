package it.unive.lisa.tutorial.test;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Objects;

/**
 * Extended sign abstract domain
 *
 * The domain tracks the sign of integer variables with more precision than
 * the basic sign domain by adding three extra elements:
 * NEGATIVE : zero or negative, i.e. x ≤ 0
 * POSITIVE : zero or positive, i.e. x ≥ 0
 * NON_ZERO: strictly non-zero, i.e. x ≠ 0
 *
 * The full lattice has 8 elements:
 * BOTTOM, STRICTLY_NEGATIVE, ZERO, STRICTLY_POSITIVE,
 * NEGATIVE, POSITIVE, NON_ZERO, TOP.
 */
public class ExtendedSigns implements BaseNonRelationalValueDomain<ExtendedSigns> {

    public static final ExtendedSigns BOTTOM = new ExtendedSigns(0);
    public static final ExtendedSigns STRICTLY_NEGATIVE = new ExtendedSigns(1);
    public static final ExtendedSigns ZERO = new ExtendedSigns(2);
    public static final ExtendedSigns STRICTLY_POSITIVE = new ExtendedSigns(3);
    public static final ExtendedSigns NEGATIVE = new ExtendedSigns(4);
    public static final ExtendedSigns POSITIVE = new ExtendedSigns(5);
    public static final ExtendedSigns NON_ZERO = new ExtendedSigns(6);
    public static final ExtendedSigns TOP = new ExtendedSigns(7);

    private final int id;

    public ExtendedSigns() {
        this(7);
    }

    private ExtendedSigns(int id) {
        this.id = id;
    }

    @Override
    public ExtendedSigns top() {
        return TOP;
    }

    @Override
    public ExtendedSigns bottom() {
        return BOTTOM;
    }

    // The less or equal relation is defined according to the lattice structure described above.
    @Override
    public boolean lessOrEqualAux(ExtendedSigns other) throws SemanticException {
        if (this == STRICTLY_NEGATIVE) return other == NEGATIVE || other == NON_ZERO;
        if (this == ZERO) return other == NEGATIVE || other == POSITIVE;
        if (this == STRICTLY_POSITIVE) return other == POSITIVE || other == NON_ZERO;
        return false;
    }

    @Override
    public ExtendedSigns lubAux(ExtendedSigns other) throws SemanticException {
        // Trivial cases: if they are equal, return this; if one is bottom, return the other; if one is top, return top.
        if (this.lessOrEqualAux(other)) return other;
        if (other.lessOrEqualAux(this)) return this;
        if (this.equals(other)) return this;
        if(this == BOTTOM) return other;
        if(other == BOTTOM) return this;
        if(this == TOP || other == TOP) return TOP;
        // Non-trivial cases:
        if ((this == STRICTLY_NEGATIVE && other == ZERO) || (this == ZERO && other == STRICTLY_NEGATIVE))
            return NEGATIVE;
        if ((this == ZERO && other == STRICTLY_POSITIVE) || (this == STRICTLY_POSITIVE && other == ZERO))
            return POSITIVE;
        if ((this == STRICTLY_NEGATIVE && other == STRICTLY_POSITIVE) || (this == STRICTLY_POSITIVE && other == STRICTLY_NEGATIVE))
            return NON_ZERO;
        return TOP;
    }

    @Override
    public StructuredRepresentation representation() {
        if (this == TOP) return Lattice.topRepresentation();
        if (this == BOTTOM) return Lattice.bottomRepresentation();
        if (this == STRICTLY_POSITIVE) return new StringRepresentation(">0");
        if (this == STRICTLY_NEGATIVE) return new StringRepresentation("<0");
        if (this == ZERO) return new StringRepresentation("=0");
        if (this == NEGATIVE) return new StringRepresentation("<=0");
        if (this == POSITIVE) return new StringRepresentation(">=0");
        if (this == NON_ZERO) return new StringRepresentation("!=0");
        return new StringRepresentation("UNKNOWN");
    }
    // The eval methods define how to evaluate constants and expressions in the abstract domain.
    @Override
    public ExtendedSigns evalNonNullConstant(
            Constant constant,
            ProgramPoint pp,
            SemanticOracle oracle)
            throws SemanticException {

        if (constant.getValue() instanceof Number) {
            long v = ((Number) constant.getValue()).longValue();
            if (v > 0) return STRICTLY_POSITIVE;
            if (v == 0) return ZERO;
            return STRICTLY_NEGATIVE;
        }
        return TOP;
    }
    // For unary and binary operators, we define the abstract semantics according to the mathematical properties of the operations.
    @Override
    public ExtendedSigns evalUnaryExpression(
            UnaryOperator operator,
            ExtendedSigns arg,
            ProgramPoint pp,
            SemanticOracle oracle)
            throws SemanticException {
        if (operator instanceof NumericNegation) return arithmeticNegate(arg);
        return TOP;
    }

    @Override
    public ExtendedSigns evalBinaryExpression(
            BinaryOperator operator,
            ExtendedSigns left,
            ExtendedSigns right,
            ProgramPoint pp,
            SemanticOracle oracle)
            throws SemanticException {
        if (operator instanceof AdditionOperator) return abstractAdd(left, right);
        if (operator instanceof SubtractionOperator) return abstractAdd(left, arithmeticNegate(right));
        if (operator instanceof MultiplicationOperator) return abstractMul(left, right);
        if (operator instanceof DivisionOperator) return abstractDiv(left, right);
        return TOP;
    }

    // The following methods implement the abstract semantics of the operations in the extended sign domain.
    private static ExtendedSigns arithmeticNegate(ExtendedSigns a) {
        if (a == STRICTLY_NEGATIVE) return STRICTLY_POSITIVE;
        if (a == STRICTLY_POSITIVE) return STRICTLY_NEGATIVE;
        if (a == NEGATIVE) return POSITIVE;
        if (a == POSITIVE) return NEGATIVE;
        return a;
    }

    private static ExtendedSigns abstractAdd(ExtendedSigns a, ExtendedSigns b) {
        if (a == BOTTOM || b == BOTTOM) return BOTTOM;
        if (a == TOP || b == TOP) return TOP;
        if (a == ZERO) return b;
        if (b == ZERO) return a;
        // For non-trivial cases, we can sort the two elements to reduce the number of cases to check.
        ExtendedSigns lo = a.id < b.id ? a : b;
        ExtendedSigns hi = a.id < b.id ? b : a;
        if (lo == STRICTLY_NEGATIVE && hi == STRICTLY_NEGATIVE) return STRICTLY_NEGATIVE;
        if (lo == STRICTLY_NEGATIVE && hi == NEGATIVE) return STRICTLY_NEGATIVE;
        if (lo == STRICTLY_POSITIVE && hi == STRICTLY_POSITIVE) return STRICTLY_POSITIVE;
        if (lo == STRICTLY_POSITIVE && hi == POSITIVE) return STRICTLY_POSITIVE;
        if (lo == NEGATIVE && hi == NEGATIVE) return NEGATIVE;
        if (lo == POSITIVE && hi == POSITIVE) return POSITIVE;
        return TOP;
    }

    private static ExtendedSigns abstractMul(ExtendedSigns a, ExtendedSigns b) {
        if (a == BOTTOM || b == BOTTOM) return BOTTOM;
        if (a == ZERO || b == ZERO) return ZERO;
        if (a == TOP || b == TOP) return TOP;
        if (a == STRICTLY_POSITIVE && b == STRICTLY_POSITIVE) return STRICTLY_POSITIVE;
        if (a == STRICTLY_NEGATIVE && b == STRICTLY_NEGATIVE) return STRICTLY_POSITIVE;
        if (a == STRICTLY_POSITIVE && b == STRICTLY_NEGATIVE) return STRICTLY_NEGATIVE;
        if (a == STRICTLY_NEGATIVE && b == STRICTLY_POSITIVE) return STRICTLY_NEGATIVE;
        if (a == NON_ZERO && b == NON_ZERO) return NON_ZERO;
        if ((a == STRICTLY_POSITIVE || a == STRICTLY_NEGATIVE) && b == NON_ZERO) return NON_ZERO;
        if (a == NON_ZERO && (b == STRICTLY_POSITIVE || b == STRICTLY_NEGATIVE)) return NON_ZERO;
        if (a == POSITIVE && b == POSITIVE) return POSITIVE;
        if (a == NEGATIVE && b == NEGATIVE) return POSITIVE;
        if (a == NEGATIVE && b == POSITIVE) return NEGATIVE;
        if (a == POSITIVE && b == NEGATIVE) return NEGATIVE;
        if (a == STRICTLY_POSITIVE && b == POSITIVE) return POSITIVE;
        if (a == POSITIVE && b == STRICTLY_POSITIVE) return POSITIVE;
        if (a == STRICTLY_NEGATIVE && b == NEGATIVE) return POSITIVE;
        if (a == NEGATIVE && b == STRICTLY_NEGATIVE) return POSITIVE;
        if (a == STRICTLY_POSITIVE && b == NEGATIVE) return NEGATIVE;
        if (a == NEGATIVE && b == STRICTLY_POSITIVE) return NEGATIVE;
        if (a == STRICTLY_NEGATIVE && b == POSITIVE) return NEGATIVE;
        if (a == POSITIVE && b == STRICTLY_NEGATIVE) return NEGATIVE;
        return TOP;
    }

    private static ExtendedSigns abstractDiv(ExtendedSigns a, ExtendedSigns b) {
        if (a == BOTTOM || b == BOTTOM) return BOTTOM;
        if (b == ZERO) return BOTTOM;
        if (a == ZERO) return ZERO;
        if (a == TOP || b == TOP) return TOP;
        // Integer division may yield 0 even when both operands are abstractly non-zero/signed,
        // so results are often only negative or positive.
        if (a == STRICTLY_POSITIVE && b == STRICTLY_POSITIVE) return POSITIVE;
        if (a == STRICTLY_NEGATIVE && b == STRICTLY_NEGATIVE) return POSITIVE;
        if (a == STRICTLY_POSITIVE && b == STRICTLY_NEGATIVE) return NEGATIVE;
        if (a == STRICTLY_NEGATIVE && b == STRICTLY_POSITIVE) return NEGATIVE;
        if (a == POSITIVE && b == STRICTLY_POSITIVE) return POSITIVE;
        if (a == NEGATIVE && b == STRICTLY_POSITIVE) return NEGATIVE;
        if (a == POSITIVE && b == STRICTLY_NEGATIVE) return NEGATIVE;
        if (a == NEGATIVE && b == STRICTLY_NEGATIVE) return POSITIVE;
        if (a == STRICTLY_POSITIVE && b == POSITIVE) return POSITIVE;
        if (a == STRICTLY_NEGATIVE && b == NEGATIVE) return POSITIVE;
        if (a == STRICTLY_POSITIVE && b == NEGATIVE) return NEGATIVE;
        if (a == STRICTLY_NEGATIVE && b == POSITIVE) return NEGATIVE;
        if (a == POSITIVE && b == POSITIVE) return POSITIVE;
        if (a == NEGATIVE && b == NEGATIVE) return POSITIVE;
        if (a == POSITIVE && b == NEGATIVE) return NEGATIVE;
        if (a == NEGATIVE && b == POSITIVE) return NEGATIVE;
        return TOP;
    }

    // The assumeBinaryExpression method refines the abstract state based on the assumption that a certain binary expression holds true at a given program point.
    @Override
    public ValueEnvironment<ExtendedSigns> assumeBinaryExpression(
            ValueEnvironment<ExtendedSigns> environment,
            BinaryOperator operator,
            ValueExpression left,
            ValueExpression right,
            ProgramPoint src,
            ProgramPoint dest,
            SemanticOracle oracle) throws SemanticException {
        if (left instanceof Identifier) {
            Identifier x = (Identifier) left;
            ExtendedSigns xValue = environment.getState(x);
            ExtendedSigns yValue = eval(right, environment, src, oracle);
            ExtendedSigns refinedValue = xValue;
            if (operator instanceof ComparisonGe) {
                if (yValue == STRICTLY_POSITIVE) {
                    refinedValue = STRICTLY_POSITIVE;
                }
                else if (yValue == ZERO) {
                    refinedValue = POSITIVE;
                }
            }
            else if (operator instanceof ComparisonGt) {
                if (yValue == ZERO || yValue == POSITIVE || yValue == STRICTLY_POSITIVE) {
                    refinedValue = STRICTLY_POSITIVE;
                }
            }
            else if (operator instanceof ComparisonLe) {
                if (yValue == STRICTLY_NEGATIVE) {
                    refinedValue = STRICTLY_NEGATIVE;
                } else if (yValue == ZERO) {
                    refinedValue = NEGATIVE;
                }
            }
            else if (operator instanceof ComparisonLt) {
                if (yValue == ZERO || yValue == NEGATIVE || yValue == STRICTLY_NEGATIVE) {
                    refinedValue = STRICTLY_NEGATIVE;
                }
            }
            else if (operator instanceof ComparisonEq) {
                refinedValue = yValue;
            }
            try {
                if (!xValue.lessOrEqual(refinedValue)) {
                    return environment.putState(x, refinedValue);
                }
            } catch (SemanticException e) {
            }
        }
        return BaseNonRelationalValueDomain.super.assumeBinaryExpression(environment, operator, left, right, src, dest, oracle);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return id == ((ExtendedSigns) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}