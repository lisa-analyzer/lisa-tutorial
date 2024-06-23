package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.annotations.Annotation;
import it.unive.lisa.program.annotations.Annotations;
import it.unive.lisa.program.annotations.matcher.AnnotationMatcher;
import it.unive.lisa.program.annotations.matcher.BasicAnnotationMatcher;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.*;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.ternary.TernaryOperator;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

public class Taint implements BaseNonRelationalValueDomain<Taint> {

	public static final Annotation TAINTED_ANNOTATION = new Annotation("lisa.taint.Tainted");

	/**
	 * An {@link AnnotationMatcher} for {@link #TAINTED_ANNOTATION}.
	 */
	public static final AnnotationMatcher TAINTED_MATCHER = new BasicAnnotationMatcher(TAINTED_ANNOTATION);

	/**
	 * The annotation used to mark clean variables, that is, sanitizers of tainted information.
	 */
	public static final Annotation CLEAN_ANNOTATION = new Annotation("lisa.taint.Clean");

	/**
	 * An {@link AnnotationMatcher} for {@link #CLEAN_ANNOTATION}.
	 */
	public static final AnnotationMatcher CLEAN_MATCHER = new BasicAnnotationMatcher(CLEAN_ANNOTATION);

	private static final Taint TAINT = new Taint(true);
	private static final Taint CLEAN = new Taint(false);
	private static final Taint BOTTOM = new Taint(null);

	Boolean taint;

	public Taint() {
		this(true);
	}

	public Taint(Boolean taint) {
		this.taint = taint;

	}

	public boolean isPossiblyTainted() {
		return this == TAINT;
	}

	@Override
	public boolean lessOrEqualAux(Taint other) throws SemanticException {
		return false;
	}

	@Override
	public Taint lubAux(Taint other) throws SemanticException {
		return TAINT;
	}

	@Override
	public Taint wideningAux(Taint other) throws SemanticException {
		return TAINT;
	}

	@Override
	public Taint top() {
		return TAINT;
	}

	@Override
	public Taint bottom() {
		return BOTTOM;
	}

	@Override
	public StructuredRepresentation representation() {
		return this == BOTTOM ? Lattice.bottomRepresentation()
				: this == CLEAN ? new StringRepresentation("_") : new StringRepresentation("#");

	}

	@Override
	public Taint evalIdentifier(
			Identifier id,
			ValueEnvironment<Taint> environment,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		Annotations annots = id.getAnnotations();
		if (annots.isEmpty())
			return BaseNonRelationalValueDomain.super.evalIdentifier(id, environment, pp, oracle);

		if (annots.contains(TAINTED_MATCHER))
			return TAINT;

		if (annots.contains(CLEAN_MATCHER))
			return CLEAN;

		return BaseNonRelationalValueDomain.super.evalIdentifier(id, environment, pp, oracle);
	}

	@Override
	public Taint evalPushAny(
			PushAny pushAny,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return top();
	}

	@Override
	public Taint evalNullConstant(
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return CLEAN;
	}

	@Override
	public Taint evalNonNullConstant(
			Constant constant,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return CLEAN;
	}

	@Override
	public Taint evalUnaryExpression(
			UnaryOperator operator,
			Taint arg,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return arg;
	}

	@Override
	public Taint evalBinaryExpression(
			BinaryOperator operator,
			Taint left,
			Taint right,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return left.lub(right);
	}

	@Override
	public Taint evalTernaryExpression(
			TernaryOperator operator,
			Taint left,
			Taint middle,
			Taint right,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return left.lub(middle).lub(right);
	}

	@Override
	public Taint evalTypeCast(
			BinaryExpression cast,
			Taint left,
			Taint right,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return left;
	}

	@Override
	public Taint evalTypeConv(
			BinaryExpression conv,
			Taint left,
			Taint right,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return left;
	}

	@Override
	public Taint evalSkip(
			Skip skip,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return BOTTOM;
	}

	@Override
	public Taint evalPushInv(
			PushInv pushInv,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return BOTTOM;
	}
}