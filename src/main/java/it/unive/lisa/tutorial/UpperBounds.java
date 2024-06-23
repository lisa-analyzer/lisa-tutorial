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

public class UpperBounds
		// instances of this class are lattice elements such that:
		// - their state (fields) hold the information contained into a single
		//   variable
		// - they provide logic for the evaluation of expressions
		extends InverseSetLattice<UpperBounds, Identifier>
		implements
		BaseNonRelationalValueDomain<
				// java requires this type parameter to have this class
				// as type in fields/methods
				UpperBounds> {

	/**
	 * The abstract top element.
	 */
	private static final UpperBounds TOP = new UpperBounds();

	/**
	 * The abstract bottom element.
	 */
	private static final UpperBounds BOTTOM = new UpperBounds(Collections.emptySet());

	/**
	 * Builds the upper bounds.
	 */
	public UpperBounds() {
		super(Collections.emptySet(), true);
	}

	/**
	 * Builds the upper bounds.
	 *
	 * @param bounds the bounds to set
	 */
	public UpperBounds(
			Set<Identifier> bounds) {
		super(bounds, false);
	}

	@Override
	public UpperBounds mk(Set<Identifier> set) {
		return new UpperBounds(set);
	}

	@Override
	public UpperBounds wideningAux(
			UpperBounds other)
			throws SemanticException {
		return other.elements.containsAll(elements) ? other : TOP;
	}

	@Override
	public UpperBounds top() {
		return TOP;
	}

	@Override
	public UpperBounds bottom() {
		return BOTTOM;
	}

	/**
	 * Adds the specified identifier of a program variable in the bounds.
	 *
	 * @param id the identifier to add in the bounds.
	 *
	 * @return the updated bounds.
	 */
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
			return environment;

		Identifier leftId = (Identifier) left;
		Identifier rightId = (Identifier) right;

		if (operator instanceof ComparisonLt) {
			UpperBounds set = environment.getState(leftId).glb(environment.getState(rightId))
					.glb(new UpperBounds(Collections.singleton(rightId)));
			return environment.putState(leftId, set);
		} else if (operator instanceof ComparisonEq) {
			UpperBounds set = environment.getState(leftId).glb(environment.getState(rightId));
			return environment.putState(leftId, set).putState(rightId, set);
		} else if (operator instanceof ComparisonLe) {
			UpperBounds set = environment.getState(leftId).glb(environment.getState(rightId));
			return environment.putState(leftId, set);
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