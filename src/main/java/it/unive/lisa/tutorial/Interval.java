package it.unive.lisa.tutorial;

/**
 * Basic interval operations can be found at https://en.wikipedia.org/wiki/Interval_arithmetic#Interval_operators
 *
 * Lattice operators can be found in https://doi.org/10.1016/j.scico.2009.04.004
 */
public class Interval {

	/*
	@Override
	public StructuredRepresentation representation() {
		if (isBottom())
			return Lattice.bottomRepresentation();

		return new StringRepresentation(interval.toString());
	}
	*/

	/*
	@Override
	public Satisfiability satisfiesBinaryExpression(
			BinaryOperator operator,
			Interval left,
			Interval right,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (left.isTop() || right.isTop())
			return Satisfiability.UNKNOWN;

		if (operator == ComparisonEq.INSTANCE) {
			Interval glb = null;
			try {
				glb = left.glb(right);
			} catch (SemanticException e) {
				return Satisfiability.UNKNOWN;
			}

			if (glb.isBottom())
				return Satisfiability.NOT_SATISFIED;
			else if (left.interval.isSingleton() && left.equals(right))
				return Satisfiability.SATISFIED;
			return Satisfiability.UNKNOWN;
		} else if (operator == ComparisonGe.INSTANCE)
			return satisfiesBinaryExpression(ComparisonLe.INSTANCE, right, left, pp, oracle);
		else if (operator == ComparisonGt.INSTANCE)
			return satisfiesBinaryExpression(ComparisonLt.INSTANCE, right, left, pp, oracle);
		else if (operator == ComparisonLe.INSTANCE) {
			Interval glb = null;
			try {
				glb = left.glb(right);
			} catch (SemanticException e) {
				return Satisfiability.UNKNOWN;
			}

			if (glb.isBottom())
				return Satisfiability.fromBoolean(left.interval.getHigh().compareTo(right.interval.getLow()) <= 0);
			// we might have a singleton as glb if the two intervals share a
			// bound
			if (glb.interval.isSingleton() && left.interval.getHigh().compareTo(right.interval.getLow()) == 0)
				return Satisfiability.SATISFIED;
			return Satisfiability.UNKNOWN;
		} else if (operator == ComparisonLt.INSTANCE) {
			Interval glb = null;
			try {
				glb = left.glb(right);
			} catch (SemanticException e) {
				return Satisfiability.UNKNOWN;
			}

			if (glb.isBottom())
				return Satisfiability.fromBoolean(left.interval.getHigh().compareTo(right.interval.getLow()) < 0);
			return Satisfiability.UNKNOWN;
		} else if (operator == ComparisonNe.INSTANCE) {
			Interval glb = null;
			try {
				glb = left.glb(right);
			} catch (SemanticException e) {
				return Satisfiability.UNKNOWN;
			}
			if (glb.isBottom())
				return Satisfiability.SATISFIED;
			return Satisfiability.UNKNOWN;
		}
		return Satisfiability.UNKNOWN;
	}

	@Override
	public ValueEnvironment<Interval> assumeBinaryExpression(
			ValueEnvironment<Interval> environment,
			BinaryOperator operator,
			ValueExpression left,
			ValueExpression right,
			ProgramPoint src,
			ProgramPoint dest,
			SemanticOracle oracle)
			throws SemanticException {
		Identifier id;
		Interval eval;
		boolean rightIsExpr;
		if (left instanceof Identifier) {
			eval = eval(right, environment, src, oracle);
			id = (Identifier) left;
			rightIsExpr = true;
		} else if (right instanceof Identifier) {
			eval = eval(left, environment, src, oracle);
			id = (Identifier) right;
			rightIsExpr = false;
		} else
			return environment;

		Interval starting = environment.getState(id);
		if (eval.isBottom() || starting.isBottom())
			return environment.bottom();

		boolean lowIsMinusInfinity = eval.interval.lowIsMinusInfinity();
		Interval low_inf = new Interval(eval.interval.getLow(), MathNumber.PLUS_INFINITY);
		Interval lowp1_inf = new Interval(eval.interval.getLow().add(MathNumber.ONE), MathNumber.PLUS_INFINITY);
		Interval inf_high = new Interval(MathNumber.MINUS_INFINITY, eval.interval.getHigh());
		Interval inf_highm1 = new Interval(MathNumber.MINUS_INFINITY, eval.interval.getHigh().subtract(MathNumber.ONE));

		Interval update = null;
		if (operator == ComparisonEq.INSTANCE)
			update = eval;
		else if (operator == ComparisonGe.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? null : starting.glb(low_inf);
			else
				update = starting.glb(inf_high);
		else if (operator == ComparisonGt.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
			else
				update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
		else if (operator == ComparisonLe.INSTANCE)
			if (rightIsExpr)
				update = starting.glb(inf_high);
			else
				update = lowIsMinusInfinity ? null : starting.glb(low_inf);
		else if (operator == ComparisonLt.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
			else
				update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);

		if (update == null)
			return environment;
		else if (update.isBottom())
			return environment.bottom();
		else
			return environment.putState(id, update);
	}
	*/
}