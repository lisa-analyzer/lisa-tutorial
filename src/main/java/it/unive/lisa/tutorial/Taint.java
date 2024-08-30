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

import java.util.Objects;

public class Taint {

	/**
	 * The annotation used to mark tainted variables.
	 */
	public static final Annotation TAINTED_ANNOTATION = new Annotation("lisa.taint.Tainted");

	/**
	 * The annotation used to mark clean variables, that is, sanitizers of tainted information.
	 */
	public static final Annotation CLEAN_ANNOTATION = new Annotation("lisa.taint.Clean");

	/**
	 * An {@link AnnotationMatcher} for {@link #TAINTED_ANNOTATION}. Annotation matchers are just utility objects that
	 * 	 * allow for conditional matching of annotations based on names, parameters, ...
	 */
	public static final AnnotationMatcher TAINTED_MATCHER = new BasicAnnotationMatcher(TAINTED_ANNOTATION);

	/**
	 * An {@link AnnotationMatcher} for {@link #CLEAN_ANNOTATION}. Annotation matchers are just utility objects that
	 * allow for conditional matching of annotations based on names, parameters, ...
	 */
	public static final AnnotationMatcher CLEAN_MATCHER = new BasicAnnotationMatcher(CLEAN_ANNOTATION);

	/*
	@Override
	public StructuredRepresentation representation() {
		// this method serializes instances of this domain
		// to a json-compatible format that will be used for dumping
		if (this == BOTTOM)
			return Lattice.bottomRepresentation();
		if (this == CLEAN)
			return new StringRepresentation("_");
		return new StringRepresentation("#");
	}
	*/
}