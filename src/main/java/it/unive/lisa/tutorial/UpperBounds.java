package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.binary.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of the upper bounds analysis of https://doi.org/10.1016/j.scico.2009.04.004
 */
public class UpperBounds
		// instances of this class are lattice elements such that:
		// - their state (fields) hold the information contained into a single
		//   variable
		// - they provide logic for the evaluation of expressions
		extends
		// the inverse set lattice is a lattice implementation provided by LiSA that uses:
		// - sets of elements as the abstract information carried by each lattice instance
		// - superset inclusion as partial order
		// - set intersection as lub
		// - set union as glb
		// this makes it so the less information you have, the more you are close to the top element
		InverseSetLattice<UpperBounds, Identifier>
		implements
		BaseNonRelationalValueDomain<
				// java requires this type parameter to have this class
				// as type in fields/methods
				UpperBounds> {

	private static final UpperBounds TOP = new UpperBounds();
	private static final UpperBounds BOTTOM = new UpperBounds(Collections.emptySet());

	public UpperBounds() { super(Collections.emptySet(), true); }

	public UpperBounds(
			Set<Identifier> bounds) {
		super(bounds, false);
	}

	@Override
	public UpperBounds mk(Set<Identifier> set) { return new UpperBounds(set); }

	@Override
	public UpperBounds wideningAux(
			UpperBounds other)
			throws SemanticException {
		// widening as provided in the paper
		return other.elements.containsAll(elements) ? other : TOP;
	}

	@Override
	public UpperBounds top() {
		// the top element of the lattice
		// if this method does not return a constant value,
		// you must override the isTop() method!
		return TOP;
	}

	@Override
	public UpperBounds bottom() {
		// the bottom element of the lattice
		// if this method does not return a constant value,
		// you must override the isBottom() method!
		return BOTTOM;
	}

	public UpperBounds add(
			Identifier id) {
		Set<Identifier> res = new HashSet<>(elements);
		res.add(id);
		return new UpperBounds(res);
	}

	// logic for evaluating conditionals below

	@Override
	public ValueEnvironment<UpperBounds> assumeBinaryExpression(
			ValueEnvironment<UpperBounds> environment,
			BinaryOperator operator,
			ValueExpression left,
			ValueExpression right,
			ProgramPoint src,
			ProgramPoint dest,
			SemanticOracle oracle) throws SemanticException {
		if (!(left instanceof Identifier && right instanceof Identifier))
			// we only process conditions between variables
			return environment;

		Identifier x = (Identifier) left;
		Identifier y = (Identifier) right;

		if (operator instanceof ComparisonLt) {
			// [[x < y]](s) = s[x -> s(x) U s(y) U {y}]
			UpperBounds s_x = environment.getState(x);
			UpperBounds s_y = environment.getState(y);
			UpperBounds y_singleton = new UpperBounds(Collections.singleton(y));
			UpperBounds set = s_x.glb(s_y).glb(y_singleton);
			return environment.putState(x, set);
		} else if (operator instanceof ComparisonEq) {
			// [[x == y]](s) = s[x,y -> s(x) U s(y)]
			UpperBounds s_x = environment.getState(x);
			UpperBounds s_y = environment.getState(y);
			UpperBounds set = s_x.glb(s_y);
			return environment.putState(x, set).putState(y, set);
		} else if (operator instanceof ComparisonLe) {
			// [[x <= y]](s) = s[x -> s(x) U s(y)]
			UpperBounds s_x = environment.getState(x);
			UpperBounds s_y = environment.getState(y);
			UpperBounds set = s_x.glb(s_y);
			return environment.putState(x, set);
		} else if (operator instanceof ComparisonGt) {
			// x > y --> y < x
			return assumeBinaryExpression(environment, ComparisonLt.INSTANCE, right, left, src, dest, oracle);
		} else if (operator instanceof ComparisonGe) {
			// x >= y --> y <= x
			return assumeBinaryExpression(environment, ComparisonLe.INSTANCE, right, left, src, dest, oracle);
		}

		return environment;
	}
}