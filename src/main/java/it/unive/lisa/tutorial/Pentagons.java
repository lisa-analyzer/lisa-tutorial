package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.*;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.numeric.Interval;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.util.numeric.MathNumber;
import it.unive.lisa.util.representation.MapRepresentation;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;

public class Pentagons
		// instances of this class are lattice elements such that:
		// - their state (fields) hold the information contained into a whole program state
		// - they provide logic for the evaluation of expressions
		implements ValueDomain<Pentagons>,
		// we exploit BaseLattice to avoid writing common-sense logic
		BaseLattice<Pentagons> {

	public ValueEnvironment<UpperBounds> upperbounds;
	public ValueEnvironment<Interval> intervals;

	public Pentagons() {
		this(new ValueEnvironment<>(new UpperBounds()).top(), new ValueEnvironment<>(new Interval()).top());
	}

	public Pentagons(
			ValueEnvironment<UpperBounds> upperbounds,
			ValueEnvironment<Interval> intervals) {
		this.upperbounds = upperbounds;
		this.intervals = intervals;
	}

	@Override
	public Pentagons top() {
		return new Pentagons(upperbounds.top(), intervals.top());
	}

	@Override
	public boolean isTop() {
		return upperbounds.isTop() && intervals.isTop();
	}

	@Override
	public Pentagons bottom() {
		return new Pentagons(upperbounds.bottom(), intervals.bottom());
	}

	@Override
	public boolean isBottom() {
		return upperbounds.isBottom() && intervals.isBottom();
	}

	// most of the operations are just forwarded to the inner domains

	@Override
	public Pentagons smallStepSemantics(
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle) throws SemanticException {
		return new Pentagons(
				upperbounds.smallStepSemantics(expression, pp, oracle),
				intervals.smallStepSemantics(expression, pp, oracle));
	}

	@Override
	public Pentagons assume(
			ValueExpression expression,
			ProgramPoint src,
			ProgramPoint dest,
			SemanticOracle oracle)
			throws SemanticException {
		return new Pentagons(
				upperbounds.assume(expression, src, dest, oracle),
				intervals.assume(expression, src, dest, oracle));
	}

	@Override
	public boolean lessOrEqualAux(Pentagons other) throws SemanticException {
		if (!this.intervals.lessOrEqual(other.intervals))
			return false;

		for (Entry<Identifier, UpperBounds> entry : other.upperbounds)
			for (Identifier bound : entry.getValue()) {
				if (this.upperbounds.getState(entry.getKey()).contains(bound))
					return false;

				Interval state = this.intervals.getState(entry.getKey());
				Interval boundState = this.intervals.getState(bound);
				if (state.isBottom() && !boundState.isBottom())
					return false;
				if (boundState.isBottom())
					return false;

				if (state.interval.getHigh().compareTo(boundState.interval.getLow()) < 0)
					return false;
			}

		return true;
	}

	@Override
	public Pentagons lubAux(
			Pentagons other)
			throws SemanticException {
		ValueEnvironment<UpperBounds> newBounds = upperbounds.lub(other.upperbounds);
		for (Entry<Identifier, UpperBounds> entry : upperbounds)
			newBounds = closeWithOther(entry.getKey(), entry.getValue(), other.intervals, newBounds);

		for (Entry<Identifier, UpperBounds> entry : other.upperbounds)
			newBounds = closeWithOther(entry.getKey(), entry.getValue(), intervals, newBounds);

		return new Pentagons(newBounds, intervals.lub(other.intervals));
	}

	private ValueEnvironment<UpperBounds> closeWithOther(
			Identifier id,
			UpperBounds bounds,
			ValueEnvironment<Interval> intervals,
			ValueEnvironment<UpperBounds> currentClosure)
			throws SemanticException {
		Set<Identifier> closure = new HashSet<>();
		Interval state = intervals.getState(id);
		if (state.isBottom())
			return currentClosure;

		for (Identifier bound : bounds) {
			Interval boundState = intervals.getState(bound);
			if (boundState.isBottom())
				continue;
			if (state.interval.getHigh().compareTo(boundState.interval.getLow()) < 0)
				closure.add(bound);
		}

		if (!closure.isEmpty())
			// glb is the union
			return currentClosure.putState(id, currentClosure.getState(id).glb(new UpperBounds(closure)));
		return currentClosure;
	}

	@Override
	public Pentagons wideningAux(
			Pentagons other)
			throws SemanticException {
		return new Pentagons(
				upperbounds.wideningAux(other.upperbounds),
				intervals.widening(other.intervals));
	}

	@Override
	public Pentagons assign(
			Identifier id,
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {

		ValueEnvironment<UpperBounds> newBounds = upperbounds.assign(id, expression, pp, oracle);
		ValueEnvironment<Interval> newIntervals = intervals.assign(id, expression, pp, oracle);

		if (expression instanceof BinaryExpression) {
			BinaryExpression be = (BinaryExpression) expression;
			BinaryOperator op = be.getOperator();

			if (op instanceof SubtractionOperator) {
				if (be.getLeft() instanceof Identifier) {
					Identifier x = (Identifier) be.getLeft();

					if (be.getRight() instanceof Identifier) {
						// r = x - y
						Identifier y = (Identifier) be.getRight();
						if (newBounds.getState(y).contains(x)) {
							newIntervals = newIntervals.putState(id, newIntervals.getState(id)
									.glb(new Interval(MathNumber.ONE, MathNumber.PLUS_INFINITY)));
						}
					} else if (be.getRight() instanceof Constant)
						// r = x + 2 (where 2 is the constant)
						newBounds = newBounds.putState(id, upperbounds.getState(x).add(x));
				}
			}

		}

		return new Pentagons(newBounds, newIntervals).closure();
	}

	private Pentagons closure() throws SemanticException {
		ValueEnvironment<UpperBounds> newBounds = new ValueEnvironment<>(upperbounds.lattice, upperbounds.getMap());

		for (Identifier id1 : intervals.getKeys()) {
			Set<Identifier> closure = new HashSet<>();
			for (Identifier id2 : intervals.getKeys())
				if (!id1.equals(id2))
					if (intervals.getState(id1).interval.getHigh().compareTo(intervals.getState(id2).interval.getLow()) < 0)
						closure.add(id2);
			if (!closure.isEmpty())
				// glb is the union
				newBounds = newBounds.putState(id1,	newBounds.getState(id1).glb(new UpperBounds(closure)));
		}

		return new Pentagons(newBounds, intervals);
	}

	@Override
	public Pentagons forgetIdentifier(
			Identifier id)
			throws SemanticException {
		return new Pentagons(
				upperbounds.forgetIdentifier(id),
				intervals.forgetIdentifier(id));
	}

	@Override
	public Pentagons forgetIdentifiersIf(
			Predicate<Identifier> test)
			throws SemanticException {
		return new Pentagons(
				upperbounds.forgetIdentifiersIf(test),
				intervals.forgetIdentifiersIf(test));
	}

	@Override
	public Satisfiability satisfies(
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return intervals.satisfies(expression, pp, oracle).glb(upperbounds.satisfies(expression, pp, oracle));
	}

	@Override
	public Pentagons pushScope(
			ScopeToken token)
			throws SemanticException {
		return new Pentagons(upperbounds.pushScope(token), intervals.pushScope(token));
	}

	@Override
	public Pentagons popScope(
			ScopeToken token)
			throws SemanticException {
		return new Pentagons(upperbounds.popScope(token), intervals.popScope(token));
	}

	@Override
	public StructuredRepresentation representation() {
		if (isTop())
			return Lattice.topRepresentation();
		if (isBottom())
			return Lattice.bottomRepresentation();
		Map<StructuredRepresentation, StructuredRepresentation> mapping = new HashMap<>();
		for (Identifier id : CollectionUtils.union(intervals.getKeys(), upperbounds.getKeys()))
			mapping.put(new StringRepresentation(id),
					new StringRepresentation(intervals.getState(id).toString() + ", " +
							upperbounds.getState(id).representation()));
		return new MapRepresentation(mapping);
	}

	@Override
	public int hashCode() {
		return Objects.hash(intervals, upperbounds);
	}

	@Override
	public boolean equals(
			Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pentagons other = (Pentagons) obj;
		return Objects.equals(intervals, other.intervals) && Objects.equals(upperbounds, other.upperbounds);
	}

	@Override
	public String toString() {
		return representation().toString();
	}

	@Override
	public boolean knowsIdentifier(
			Identifier id) {
		return intervals.knowsIdentifier(id) || upperbounds.knowsIdentifier(id);
	}
}