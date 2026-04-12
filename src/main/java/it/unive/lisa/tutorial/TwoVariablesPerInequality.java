package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGt;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLt;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TwoVariablesPerInequality implements ValueDomain<TwoVariablesPerInequality> {

    private static final double EPS = 1e-9; // Tolerance for floating-point comparisons
    private static final int MAX_INEQS_PER_PLANE = 30;

    public static final TwoVariablesPerInequality TOP =
            new TwoVariablesPerInequality(true, false, Collections.emptyMap(), Collections.emptyMap());

    public static final TwoVariablesPerInequality BOTTOM =
            new TwoVariablesPerInequality(false, true, Collections.emptyMap(), Collections.emptyMap());

    private final boolean isTop;
    private final boolean isBottom;
    private final Map<VarPair, List<LinearInequality>> binaryPlanes;
    private final Map<Identifier, double[]> unaryBounds;

    private TwoVariablesPerInequality(
            boolean top, boolean bottom,
            Map<VarPair, List<LinearInequality>> binary,
            Map<Identifier, double[]> unary) {
        this.isTop        = top;
        this.isBottom     = bottom;
        this.binaryPlanes = binary;
        this.unaryBounds  = unary;
    }

    // Returns true for LiSA-internal identifiers that should not be tracked numerically.
    private static boolean isSpecial(Identifier id) {
        if (id == null) return true;
        String name = id.getName();
        if (name == null || name.isBlank()) name = id.toString();
        return name.contains("this") || name.contains("heap")
                || name.contains("pp@") || name.contains("&");
    }

    // Returns true if any variable in the inequality is a synthetic LiSA identifier.
    private static boolean hasSpecial(LinearInequality ineq) {
        for (Identifier id : ineq.vars())
            if (isSpecial(id)) return true;
        return false;
    }

    // Builds an abstract state from a collection of inequalities by running completion then partitioning into unary/binary maps.
    public static TwoVariablesPerInequality of(Collection<LinearInequality> ineqs) {
        if (ineqs == null || ineqs.isEmpty()) return TOP;

        List<LinearInequality> cleaned = new ArrayList<>();
        for (LinearInequality i : ineqs)
            if (i != null && !hasSpecial(i))
                cleaned.add(i);
        if (cleaned.isEmpty()) return TOP;

        List<LinearInequality> completed = complete(cleaned);
        if (completed == null) return BOTTOM;

        Map<VarPair, List<LinearInequality>> binary = new HashMap<>();
        Map<Identifier, double[]> unary = new HashMap<>();

        for (LinearInequality ineq : completed) {
            int sz = ineq.varCount();
            if (sz == 0) {
                if (ineq.rhs < -EPS) return BOTTOM;
            } else if (sz == 1) {
                Identifier v = ineq.singleVar();
                if (isSpecial(v)) continue;
                double coeff = ineq.coefficient(v);
                double bound = ineq.rhs / coeff;
                double[] iv = unary.computeIfAbsent(v,
                        k -> new double[]{ Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY });
                if (coeff > 0) iv[1] = Math.min(iv[1], bound);
                else           iv[0] = Math.max(iv[0], bound);
            } else {
                VarPair pair = VarPair.of(ineq);
                if (pair == null || isSpecial(pair.x) || isSpecial(pair.y)) continue;
                binary.computeIfAbsent(pair, k -> new ArrayList<>()).add(ineq);
            }
        }

        for (Map.Entry<VarPair, List<LinearInequality>> e : binary.entrySet()) {
            List<LinearInequality> filtered = filterPlanar(e.getValue(), e.getKey());
            if (filtered == null) return BOTTOM;
            e.setValue(filtered);
        }

        for (double[] iv : unary.values())
            if (iv[0] > iv[1] + EPS) return BOTTOM;

        if (binary.isEmpty() && unary.isEmpty()) return TOP;
        return new TwoVariablesPerInequality(false, false, binary, unary);
    }

    /**
     * Implements completion (Definition 10): repeatedly applies pairwise
     * Fourier-Motzkin elimination (resultant) on all current inequalities,
     * adds the new implied constraints, filters redundant ones, and iterates
     * for at most ceil(log2(d-1)) + 2 steps, where d is the number of distinct
     * variables. Terminates early if no new inequalities are produced.
     * Returns null if the system is unsatisfiable.
     */
    private static List<LinearInequality> complete(List<LinearInequality> initial) {
        Set<Identifier> allVars = new HashSet<>();
        for (LinearInequality ineq : initial) allVars.addAll(ineq.vars());
        int d = allVars.size();
        int maxIter = (d <= 1) ? 2 : (int) Math.ceil(Math.log(Math.max(d - 1, 1)) / Math.log(2)) + 2;
        Set<LinearInequality> current = new HashSet<>(initial);
        for (int iter = 0; iter <= maxIter; iter++) {
            List<LinearInequality> list = new ArrayList<>(current);
            Set<LinearInequality> newIneqs = new HashSet<>();
            int n = list.size();
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    newIneqs.addAll(resultant(list.get(i), list.get(j)));
            newIneqs.removeAll(current);
            current.addAll(newIneqs);
            for (LinearInequality ineq : current)
                if (ineq.varCount() == 0 && ineq.rhs < -EPS) return null;
            Set<LinearInequality> filtered = filterAll(current);
            if (filtered == null) return null;
            current = filtered;
            if (newIneqs.isEmpty() && iter >= maxIter) break;
        }
        return new ArrayList<>(current);
    }

    /**
     * Applies Fourier-Motzkin elimination (Definition 9) to a pair of inequalities
     * sharing a variable with opposite-signed coefficients, eliminating that variable
     * and returning the implied inequality on the remaining variables.
     * The result is L2-normalized and returned only if it contains at most 2 variables
     * and no special identifiers.
     */
  private static List<LinearInequality> resultant(LinearInequality t1, LinearInequality t2) {
        List<LinearInequality> results = new ArrayList<>();

        Set<Identifier> shared = new HashSet<>(t1.vars());
        shared.retainAll(t2.vars());

        for (Identifier pivot : shared) {
            if (isSpecial(pivot)) continue;
            double c1 = t1.coefficient(pivot);
            double c2 = t2.coefficient(pivot);
            if (c1 * c2 >= 0) continue;

            double scale1 = Math.abs(c2);
            double scale2 = Math.abs(c1);

            Map<Identifier, Double> newCoeffs = new HashMap<>();
            for (Map.Entry<Identifier, Double> e : t1.coefficients.entrySet())
                if (!e.getKey().equals(pivot))
                    newCoeffs.merge(e.getKey(), e.getValue() * scale1, Double::sum);
            for (Map.Entry<Identifier, Double> e : t2.coefficients.entrySet())
                if (!e.getKey().equals(pivot))
                    newCoeffs.merge(e.getKey(), e.getValue() * scale2, Double::sum);

            newCoeffs.entrySet().removeIf(e -> Math.abs(e.getValue()) < EPS);
            double newRhs = t1.rhs * scale1 + t2.rhs * scale2;

            if (newCoeffs.size() <= 2) {
                LinearInequality res = new LinearInequality(newCoeffs, newRhs);
                if (!hasSpecial(res)) results.add(res);
            }
        }
        return results;
    }

    /**
     * Filters a set of inequalities by grouping them on their canonical
     * variable-name key (variables sorted alphabetically) to avoid
     * Map-key issues with Set<Identifier>, then applies per-group filtering:
     * - 0 variables: checks for contradiction (0 <= c with c < 0)
     * - 1 variable:  keeps only the tightest upper and lower bound, detects contradiction if lower > upper
     * - 2 variables: delegates to filterPlanar for geometric redundancy removal
     * Inequalities involving special identifiers are skipped.
     * Returns null if any group yields a contradiction.
     */
    private static Set<LinearInequality> filterAll(Set<LinearInequality> ineqs) {
        Map<String, List<LinearInequality>> groups   = new LinkedHashMap<>();
        Map<String, List<Identifier>>       keyToIds = new LinkedHashMap<>();

        for (LinearInequality ineq : ineqs) {
            if (hasSpecial(ineq)) continue;
            List<Identifier> sortedIds = ineq.vars().stream()
                    .sorted(Comparator.comparing(Identifier::getName))
                    .collect(Collectors.toList());
            String key = sortedIds.stream()
                    .map(Identifier::getName)
                    .collect(Collectors.joining(","));
            groups  .computeIfAbsent(key, k -> new ArrayList<>()).add(ineq);
            keyToIds.putIfAbsent(key, sortedIds);
        }

        Set<LinearInequality> result = new HashSet<>();

        for (Map.Entry<String, List<LinearInequality>> e : groups.entrySet()) {
            List<Identifier>       ids   = keyToIds.get(e.getKey());
            List<LinearInequality> group = e.getValue();
            int nVars = ids.size();

            if (nVars == 0) {
                for (LinearInequality ineq : group)
                    if (ineq.rhs < -EPS) return null;

            } else if (nVars == 1) {
                Identifier v = ids.get(0);
                double upper = Double.POSITIVE_INFINITY;
                double lower = Double.NEGATIVE_INFINITY;
                for (LinearInequality ineq : group) {
                    double coeff = ineq.coefficient(v);
                    double bound = ineq.rhs / coeff;
                    if (coeff > 0) upper = Math.min(upper, bound);
                    else           lower = Math.max(lower, bound);
                }
                if (lower > upper + EPS) return null;
                if (!Double.isInfinite(upper)) {
                    Map<Identifier, Double> c = new HashMap<>(); c.put(v, 1.0);
                    result.add(new LinearInequality(c, upper));
                }
                if (!Double.isInfinite(lower)) {
                    Map<Identifier, Double> c = new HashMap<>(); c.put(v, -1.0);
                    result.add(new LinearInequality(c, -lower));
                }

            } else if (nVars == 2) {
                Identifier x = ids.get(0), y = ids.get(1);
                if (isSpecial(x) || isSpecial(y)) continue;
                VarPair pair = new VarPair(x, y);
                List<LinearInequality> filtered = filterPlanar(group, pair);
                if (filtered == null) return null;
                result.addAll(filtered);
            }
        }
        return result;
    }

    /**
     * Applies the paper's Fig.1 redundancy-removal on one {x,y} plane:
     * - Returns null if a contradiction (0 <= c with c < 0) is detected
     * - Sorts inequalities by angle theta (Definition 6)
     * - Deduplicates same-direction inequalities, keeping the tightest (smallest right hand size)
     * - Iteratively removes any inequality implied by its two cyclic neighbours
     *   until no more removals are possible
     */
    private static List<LinearInequality> filterPlanar(
            List<LinearInequality> ineqs, VarPair pair) {

        if (ineqs.isEmpty()) return ineqs;
        Identifier x = pair.x, y = pair.y;

        for (LinearInequality ineq : ineqs)
            if (ineq.varCount() == 0 && ineq.rhs < -EPS) return null;

        List<LinearInequality> sorted = new ArrayList<>(ineqs);
        sorted.sort(Comparator.comparingDouble(ineq -> angleOf(ineq, x, y)));

        List<LinearInequality> deduped = new ArrayList<>();
        for (LinearInequality cur : sorted) {
            if (deduped.isEmpty() || !sameAngle(cur, deduped.get(deduped.size()-1), x, y)) {
                deduped.add(cur);
            } else {
                LinearInequality prev = deduped.get(deduped.size()-1);
                if (cur.rhs < prev.rhs - EPS)
                    deduped.set(deduped.size()-1, cur);
            }
        }
        sorted = deduped;

        boolean changed = true;
        while (changed && sorted.size() > 1) {
            changed = false;
            List<LinearInequality> next = new ArrayList<>();
            int n = sorted.size();
            for (int i = 0; i < n; i++) {
                LinearInequality tc    = sorted.get(i);
                LinearInequality tprev = sorted.get((i - 1 + n) % n);
                LinearInequality tnext = sorted.get((i + 1) % n);
                if (entailsTwoPlusOne(tprev, tnext, tc, x, y)) {
                    changed = true;
                } else {
                    next.add(tc);
                }
            }
            if (changed) sorted = next;
        }
        return sorted;
    }

    /**
     * Checks {t1, t2} |= t3 using Proposition 2:
     * - First tries single-inequality entailment: t1 |= t3 or t2 |= t3 alone
     * - Otherwise, uses Cramer's rule to find non-negative coefficients λ1, λ2 >= 0
     *   such that t3 = λ1*t1 + λ2*t2 (t3 is a non-negative linear combination of t1 and t2)
     * - If t1 and t2 are parallel (det ≈ 0), no unique intersection exists -> returns false
     * - Returns true iff λ1*c1 + λ2*c2 <= c (the combination witnesses the entailment)
     */    
    private static boolean entailsTwoPlusOne(
            LinearInequality t1, LinearInequality t2, LinearInequality t3,
            Identifier x, Identifier y) {

        if (entailsOne(t1, t3, x, y)) return true;
        if (entailsOne(t2, t3, x, y)) return true;

        double a1 = t1.coefficient(x), b1 = t1.coefficient(y), c1 = t1.rhs;
        double a2 = t2.coefficient(x), b2 = t2.coefficient(y), c2 = t2.rhs;
        double a  = t3.coefficient(x), b  = t3.coefficient(y), c  = t3.rhs;

        double det = a1*b2 - a2*b1;
        if (Math.abs(det) < EPS) return false;

        double lambda1 = (a*b2 - a2*b) / det;
        double lambda2 = (a1*b - a*b1) / det;

        if (lambda1 < -EPS || lambda2 < -EPS) return false;
        return lambda1*c1 + lambda2*c2 <= c + EPS;
    }

    /**
     * Checks {t1} |= t2 in the parallel case:
     * - Verifies t1 and t2 are parallel: a1*b - a*b1 ≈ 0 (proportional normal vectors)
     * - Verifies same orientation: a1*a >= 0 and b1*b >= 0 (not opposite directions)
     * - Computes the proportionality factor (a/a1 or b/b1) to scale c1,
     *   then checks scaled_c1 <= c (t1's half-space is contained in t2's)
     * - Degenerate case (both coefficients ≈ 0): checks c1 >= 0 or c >= 0
     */
    private static boolean entailsOne(
            LinearInequality t1, LinearInequality t2,
            Identifier x, Identifier y) {

        double a1 = t1.coefficient(x), b1 = t1.coefficient(y), c1 = t1.rhs;
        double a  = t2.coefficient(x), b  = t2.coefficient(y), c  = t2.rhs;

        if (Math.abs(a1*b - a*b1) > EPS) return false;
        if (a1*a < -EPS || b1*b < -EPS)  return false;

        if (Math.abs(a1) > EPS) return (a/a1)*c1 <= c + EPS;
        if (Math.abs(b1) > EPS) return (b/b1)*c1 <= c + EPS;
        return c1 >= -EPS || c >= -EPS;
    }

    @Override public TwoVariablesPerInequality top()    { return TOP; }
    @Override public boolean isTop()                    { return isTop; }
    @Override public TwoVariablesPerInequality bottom() { return BOTTOM; }
    @Override public boolean isBottom()                 { return isBottom; }

    @Override
    public StructuredRepresentation representation() {
        if (isTop)    return Lattice.topRepresentation();
        if (isBottom) return Lattice.bottomRepresentation();
        return new StringRepresentation(toString());
    }

    /**
     * Checks this |= other (Proposition 7), i.e. this is more precise than other:
     * - Trivial cases: BOTTOM |= anything, anything |= TOP
     * - For each binary plane {x,y} in other:
     *     - if this has no such plane -> false (this has no constraints on that pair)
     *     - otherwise delegates to planarEntails to check this's plane |= other's plane
     * - For each unary bound in other:
     *     - if this has no bound for that variable -> false
     *     - checks this's interval is contained in other's:
     *       this.upper <= other.upper and this.lower >= other.lower (with EPS tolerance)
     */    @Override
    public boolean lessOrEqual(TwoVariablesPerInequality other) throws SemanticException {
        if (isBottom() || other.isTop())    return true;
        if (isTop()    || other.isBottom()) return false;

        for (Map.Entry<VarPair, List<LinearInequality>> e : other.binaryPlanes.entrySet()) {
            VarPair pair = e.getKey();
            List<LinearInequality> thisPlane = binaryPlanes.get(pair);
            if (thisPlane == null) return false;
            if (!planarEntails(thisPlane, e.getValue(), pair)) return false;
        }

        for (Map.Entry<Identifier, double[]> e : other.unaryBounds.entrySet()) {
            double[] oIv = e.getValue();
            double[] tIv = unaryBounds.get(e.getKey());
            if (tIv == null) return false;
            if (tIv[1] > oIv[1] + EPS) return false;
            if (tIv[0] < oIv[0] - EPS) return false;
        }
        return true;
    }

    /**
     * Checks whether every constraint in plane2 is implied by plane1,
     * by working in the {x,y} plane (Fig.4).
     * Intuitively: plane1 |= plane2 means the region described by plane1
     * is contained in the region described by plane2.
     * For each constraint t in plane2 (sorted by angle), we find the two
     * neighbouring constraints in plane1 that "surround" t angularly,
     * and check that together they imply t.
     * Returns false as soon as one constraint in plane2 is not implied,
     * true if all constraints pass.
     */
    private static boolean planarEntails(
            List<LinearInequality> plane1,
            List<LinearInequality> plane2,
            VarPair pair) {

        if (plane2.isEmpty()) return true;
        if (plane1.isEmpty()) return false;

        Identifier x = pair.x, y = pair.y;
        int n = plane1.size();

        List<LinearInequality> sorted2 = new ArrayList<>(plane2);
        sorted2.sort(Comparator.comparingDouble(ineq -> angleOf(ineq, x, y)));

        int u = 0, l = n - 1;
        for (LinearInequality t : sorted2) {
            double ta = angleOf(t, x, y);
            while (u < n && angleOf(plane1.get(u), x, y) < ta - EPS) {
                l = u; u++;
            }
            LinearInequality tl = plane1.get(l % n);
            LinearInequality tu = plane1.get(u % n);
            if (!entailsTwoPlusOne(tl, tu, t, x, y)) return false;
        }
        return true;
    }

    /**
     * Least upper bound: for each variable pair in both operands, computes the convex hull
     * of their planar constraints; pairs missing in either operand are dropped; unary bounds
     * are joined as lo=min, hi=max.
     */
    @Override
    public TwoVariablesPerInequality lub(TwoVariablesPerInequality other) throws SemanticException {
        if (isTop()    || other.isTop())    return TOP;
        if (isBottom()) return other;
        if (other.isBottom()) return this;

        Map<VarPair, List<LinearInequality>> newBinary = new HashMap<>();
        for (VarPair pair : binaryPlanes.keySet()) {
            if (!other.binaryPlanes.containsKey(pair)) continue;
            List<LinearInequality> hull = planarConvexHull(
                    binaryPlanes.get(pair), other.binaryPlanes.get(pair), pair);
            if (hull == null || hull.isEmpty()) continue;
            List<LinearInequality> filtered = filterPlanar(hull, pair);
            if (filtered != null && !filtered.isEmpty())
                newBinary.put(pair, filtered);
        }

        Map<Identifier, double[]> newUnary = new HashMap<>();
        Set<Identifier> allVars = new HashSet<>(unaryBounds.keySet());
        allVars.addAll(other.unaryBounds.keySet());

        for (Identifier v : allVars) {
            if (isSpecial(v)) continue;
            double[] iv1 = unaryBounds.getOrDefault(v,
                    new double[]{ Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY });
            double[] iv2 = other.unaryBounds.getOrDefault(v,
                    new double[]{ Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY });
            double lo = Math.min(iv1[0], iv2[0]);
            double hi = Math.max(iv1[1], iv2[1]);
            if (!Double.isInfinite(lo) || !Double.isInfinite(hi))
                newUnary.put(v, new double[]{ lo, hi });
        }

        if (newBinary.isEmpty() && newUnary.isEmpty()) return TOP;
        return new TwoVariablesPerInequality(false, false, newBinary, newUnary);
    }

    /**
     * Greatest lower bound: collects all constraints from both operands into one list
     * and calls of() to run completion and detect contradictions.
     */
    @Override
    public TwoVariablesPerInequality glb(TwoVariablesPerInequality other) throws SemanticException {
        if (isTop())    return other;
        if (other.isTop()) return this;
        if (isBottom() || other.isBottom()) return BOTTOM;

        List<LinearInequality> combined = new ArrayList<>(this.toInequalityList());
        combined.addAll(other.toInequalityList());
        return TwoVariablesPerInequality.of(combined);
    }

    /**
     * Computes the convex hull of two planar polyhedra (Definition 11):
     * handles the same-direction shortcut, then computes extreme points/rays,
     * builds far points along rays, runs Graham scan, and converts hull edges to inequalities.
     */
    private static List<LinearInequality> planarConvexHull(
            List<LinearInequality> p1, List<LinearInequality> p2, VarPair pair) {

        Identifier x = pair.x, y = pair.y;

        if (p1 == null || p1.isEmpty()) return p2 == null ? new ArrayList<>() : new ArrayList<>(p2);
        if (p2 == null || p2.isEmpty()) return new ArrayList<>(p1);
        if (sameDirsections(p1, p2, x, y)) {
            List<LinearInequality> result = new ArrayList<>();
            for (LinearInequality i1 : p1) {
                double angle1 = angleOf(i1, x, y);
                LinearInequality i2 = p2.stream()
                        .filter(i -> Math.abs(angleOf(i, x, y) - angle1) < EPS)
                        .findFirst().orElse(null);
                double rhs = (i2 == null) ? i1.rhs : Math.max(i1.rhs, i2.rhs);
                result.add(new LinearInequality(new HashMap<>(i1.coefficients),
                        rhs * norm(i1)));
            }
            return result;
        }
        ExtremeResult e1 = computeExtreme(p1, x, y);
        ExtremeResult e2 = computeExtreme(p2, x, y);

        List<double[]> allVertices = new ArrayList<>();
        allVertices.addAll(e1.vertices);
        allVertices.addAll(e2.vertices);

        List<double[]> allRays = new ArrayList<>();
        allRays.addAll(e1.rays);
        allRays.addAll(e2.rays);

        if (allVertices.size() == 1 && allRays.isEmpty()) {
            double px = allVertices.get(0)[0], py = allVertices.get(0)[1];
            return pointToIneqs(px, py, x, y);
        }

        double m = 1.0;
        for (double[] v : allVertices) {
            m = Math.max(m, Math.abs(v[0]));
            m = Math.max(m, Math.abs(v[1]));
        }
        m += 1.0;

        List<double[]> points = new ArrayList<>(allVertices);
        double scale = 2.0 * Math.sqrt(2.0) * m;
        for (double[] v : allVertices) {
            for (double[] r : allRays) {
                points.add(new double[]{ v[0] + scale * r[0],
                        v[1] + scale * r[1] });
            }
        }

        if (allVertices.isEmpty()) {
            for (double[] r : allRays) {
                points.add(new double[]{ scale * r[0], scale * r[1] });
            }
        }

        if (points.isEmpty()) return new ArrayList<>();

        List<double[]> hull = grahamScan(points);
        if (hull.size() < 2) return new ArrayList<>();

        List<LinearInequality> result = hullToIneqs(hull, m, x, y);

        return result;
    }


    /**
     * Checks whether two inequalities have the same direction (angle) within EPS tolerance.
     **/
    private static boolean sameDirsections(
            List<LinearInequality> p1, List<LinearInequality> p2,
            Identifier x, Identifier y) {
        if (p1.size() != p2.size()) return false;
        for (LinearInequality i1 : p1) {
            double a = angleOf(i1, x, y);
            boolean found = p2.stream()
                    .anyMatch(i2 -> Math.abs(angleOf(i2, x, y) - a) < EPS);
            if (!found) return false;
        }
        return true;
    }


    /**
     * Computes the L2 norm of the normal vector of the inequality,
     * used to scale the right hand side when merging same-direction inequalities in planarConvexHull.
     **/
    private static double norm(LinearInequality ineq) {
        double s = 0;
        for (double v : ineq.coefficients.values())
            s += v * v;
        return Math.sqrt(s);
    }


    /**
     * Computes vertices and rays of a planar polyhedron (Fig.2):
     * for each inequality, checks whether the angular gap to neighbours is ≥ PI
     * (unbounded direction -> ray) or not (bounded corner -> vertex).
     **/
    private static ExtremeResult computeExtreme(
            List<LinearInequality> plane, Identifier x, Identifier y) {

        ExtremeResult res = new ExtremeResult();
        int n = plane.size();
        if (n == 0) return res;

        for (int i = 0; i < n; i++) {
            LinearInequality ti    = plane.get(i);
            LinearInequality tprev = plane.get((i - 1 + n) % n);
            LinearInequality tnext = plane.get((i + 1) % n);

            double angPrev = angleDiff(ti, tprev, x, y);
            double angNext = angleDiff(tnext, ti, x, y);

            boolean dpre  = (angPrev >= Math.PI - EPS) || n == 1;
            boolean dpost = (angNext >= Math.PI - EPS) || n == 1;

            if (dpre)  res.rays.add(perpRay(ti, x, y, true));
            if (dpost) res.rays.add(perpRay(ti, x, y, false));

            if (!dpre && !dpost) {
                double[] pt = intersectBoundaries(ti, tnext, x, y);
                if (pt != null) res.vertices.add(pt);
            }

            if (dpre && dpost) {
                res.vertices.add(pointOnBoundary(ti, x, y));
                if (n == 1) {
                    double a = ti.coefficient(x), b = ti.coefficient(y);
                    double norm = Math.sqrt(a*a + b*b);
                    if (norm > EPS)
                        res.rays.add(new double[]{ -a/norm, -b/norm });
                }
            }
        }
        return res;
    }

    /**
     * Returns the unit perpendicular ray along the boundary of ineq
     * (forward = left normal, backward = right normal).
     **/
    private static double[] perpRay(LinearInequality ineq,
                                    Identifier x, Identifier y,
                                    boolean forward) {
        double a = ineq.coefficient(x), b = ineq.coefficient(y);
        double norm = Math.sqrt(a*a + b*b);
        if (norm < EPS) return new double[]{1.0, 0.0};
        double dx = forward ? -b :  b;
        double dy = forward ?  a : -a;
        return new double[]{dx/norm, dy/norm};
    }

    /**
     * Returns the intersection point of the boundary lines of i1 and i2,
     * or null if they are parallel.
     **/
    private static double[] intersectBoundaries(LinearInequality i1, LinearInequality i2,
                                                Identifier x, Identifier y) {
        double a1 = i1.coefficient(x), b1 = i1.coefficient(y), c1 = i1.rhs;
        double a2 = i2.coefficient(x), b2 = i2.coefficient(y), c2 = i2.rhs;
        double det = a1*b2 - a2*b1;
        if (Math.abs(det) < EPS) return null;
        return new double[]{ (c1*b2 - b1*c2)/det, (a1*c2 - c1*a2)/det };
    }

    /** Returns an arbitrary point that lies on the boundary hyperplane of ineq. */
    private static double[] pointOnBoundary(LinearInequality ineq,
                                            Identifier x, Identifier y) {
        double a = ineq.coefficient(x), b = ineq.coefficient(y), c = ineq.rhs;
        if (Math.abs(a) > EPS) return new double[]{ c/a, 0.0 };
        if (Math.abs(b) > EPS) return new double[]{ 0.0, c/b };
        return new double[]{ 0.0, 0.0 };
    }

    /** Encodes a single fixed point as four inequalities: x<=px, -x<=-px, y<=py, -y<=-py. */
    private static List<LinearInequality> pointToIneqs(double px, double py,
                                                       Identifier x, Identifier y) {
        List<LinearInequality> r = new ArrayList<>();
        Map<Identifier, Double> c;
        c = new HashMap<>(); c.put(x,  1.0); r.add(new LinearInequality(c,  px));
        c = new HashMap<>(); c.put(x, -1.0); r.add(new LinearInequality(c, -px));
        c = new HashMap<>(); c.put(y,  1.0); r.add(new LinearInequality(c,  py));
        c = new HashMap<>(); c.put(y, -1.0); r.add(new LinearInequality(c, -py));
        return r;
    }

    /** Andrew's monotone-chain convex hull; collinear boundary points are retained (cross >= 0). */
    private static List<double[]> grahamScan(List<double[]> points) {
        if (points.size() <= 2) return new ArrayList<>(points);

        List<double[]> pts = new ArrayList<>(points);
        pts.sort((a, b) -> {
            if (Math.abs(a[0]-b[0]) > EPS) return Double.compare(a[0], b[0]);
            return Double.compare(a[1], b[1]);
        });

        List<double[]> lower = new ArrayList<>();
        for (double[] p : pts) {
            while (lower.size() >= 2 &&
                    cross(lower.get(lower.size()-2), lower.get(lower.size()-1), p) < -EPS)
                lower.remove(lower.size()-1);
            lower.add(p);
        }

        List<double[]> upper = new ArrayList<>();
        for (int i = pts.size()-1; i >= 0; i--) {
            double[] p = pts.get(i);
            while (upper.size() >= 2 &&
                    cross(upper.get(upper.size()-2), upper.get(upper.size()-1), p) < -EPS)
                upper.remove(upper.size()-1);
            upper.add(p);
        }

        lower.remove(lower.size()-1);
        upper.remove(upper.size()-1);
        lower.addAll(upper);
        return lower;
    }

    /** Cross product of OA x OB (O as origin);
     * positive if OAB makes a left turn, negative for right turn, zero if collinear. */
    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0]);
    }

    /** Walks the convex hull and converts each real edge (at least one endpoint inside the bounding box)
     * to a LinearInequality; inserts a collinear separator when two consecutive edges share the same angle. */
    private static List<LinearInequality> hullToIneqs(
            List<double[]> hull, double m, Identifier x, Identifier y) {

        List<LinearInequality> result = new ArrayList<>();
        int n = hull.size();
        if (n < 2) return result;

        LinearInequality prevIneq = edgeToIneq(hull.get(n-1), hull.get(0), x, y, hull);

        for (int i = 0; i < n; i++) {
            double[] p1 = hull.get(i);
            double[] p2 = hull.get((i+1) % n);

            boolean p1Real = Math.abs(p1[0]) < m && Math.abs(p1[1]) < m;
            boolean p2Real = Math.abs(p2[0]) < m && Math.abs(p2[1]) < m;

            if (!p1Real && !p2Real) continue;

            LinearInequality ineq = edgeToIneq(p1, p2, x, y, hull);
            if (ineq == null) continue;
            if (!p1Real || !p2Real)
                if(ineq.varCount() >1 )
                    continue;
            if (prevIneq != null && sameAngle(ineq, prevIneq, x, y)) {
                LinearInequality sep = collinearSeparator(p1, p2, x, y);
                if (sep != null) result.add(sep);
            }

            result.add(ineq);
            prevIneq = ineq;
        }
        return result;
    }

    /** Converts a directed hull edge (p1 -> p2) to the half-space inequality
     * with the hull interior on its feasible side. */
    private static LinearInequality edgeToIneq(
            double[] p1, double[] p2,
            Identifier x, Identifier y, List<double[]> hull) {

        double a = p2[1] - p1[1];
        double b = p1[0] - p2[0];
        double c = a*p1[0] + b*p1[1];
        if (Math.abs(a) < EPS && Math.abs(b) < EPS) return null;

        double cx = 0, cy = 0;
        for (double[] p : hull) { cx += p[0]; cy += p[1]; }
        cx /= hull.size(); cy /= hull.size();
        if (a*cx + b*cy > c + EPS) { a = -a; b = -b; c = -c; }

        Map<Identifier, Double> coeffs = new HashMap<>();
        if (Math.abs(a) > EPS) coeffs.put(x, a);
        if (Math.abs(b) > EPS) coeffs.put(y, b);
        if (coeffs.isEmpty()) return null;
        return new LinearInequality(coeffs, c);
    }

    private static LinearInequality collinearSeparator(
            double[] p1, double[] p2, Identifier x, Identifier y) {

        Map<Identifier, Double> c = new HashMap<>();
        if (Math.abs(p1[0] - p2[0]) < EPS) {
            double sign = (p2[1] > p1[1]) ? 1.0 : -1.0;
            c.put(x, sign);
            return new LinearInequality(c, sign * p1[0]);
        } else {
            double sign = (p2[0] > p1[0]) ? 1.0 : -1.0;
            c.put(y, sign);
            return new LinearInequality(c, sign * p1[1]);
        }
    }

    /** Handles variable assignment: in-place increments (x = x ± c) are treated via rhs-shifting to preserve relational
     * constraints; other forms forget then assert the new value. */
    @Override
    public TwoVariablesPerInequality assign(
            Identifier id, ValueExpression expr,
            ProgramPoint pp, SemanticOracle oracle) throws SemanticException {

        if (isBottom()) return this;
        if (isSpecial(id)) return this;

        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            if (bin.getLeft().equals(id) && bin.getRight() instanceof Constant) {
                Object v = ((Constant) bin.getRight()).getValue();
                if (v instanceof Number) {
                    double c = ((Number) v).doubleValue();
                    boolean isAdd = bin.getOperator() instanceof AdditionOperator;
                    boolean isSub = bin.getOperator() instanceof SubtractionOperator;
                    if (isAdd || isSub) {
                        if (isSub) c = -c;
                        return shiftVariable(id, c);
                    }
                }
            }
        }

        TwoVariablesPerInequality state = forgetIdentifier(id);
        List<LinearInequality> newIneqs = new ArrayList<>(state.toInequalityList());
        newIneqs.addAll(inequalitiesFromExpr(id, expr, state));
        return TwoVariablesPerInequality.of(newIneqs);
    }

    /** Shifts the rhs of every inequality involving id by coeff(id) × delta, implementing x := x + delta without losing relational information. */
    private TwoVariablesPerInequality shiftVariable(Identifier id, double delta) {
        List<LinearInequality> shifted = new ArrayList<>();
        for (LinearInequality ineq : toInequalityList()) {
            double coeff = ineq.coefficient(id);
            if (Math.abs(coeff) < EPS) {
                shifted.add(ineq);
            } else {
                Map<Identifier, Double> rawCoeffs = new HashMap<>(ineq.coefficients);
                double newRhs = ineq.rhs + coeff * delta;
                shifted.add(new LinearInequality(rawCoeffs, newRhs));
            }
        }
        return TwoVariablesPerInequality.of(shifted);
    }

    /** Generates the one or two LinearInequality objects that encode the assignment id = expr; for constant assignments also links id to already-known variables. */
    private List<LinearInequality> inequalitiesFromExpr(
            Identifier id, ValueExpression expr,
            TwoVariablesPerInequality state) {

        List<LinearInequality> result = new ArrayList<>();
        if (isSpecial(id)) return result;

        if (expr instanceof Identifier) {
            Identifier rhs = (Identifier) expr;
            if (isSpecial(rhs)) return result;
            Map<Identifier, Double> c1 = new HashMap<>();
            c1.put(id, 1.0); c1.put(rhs, -1.0);
            result.add(new LinearInequality(c1, 0.0));
            Map<Identifier, Double> c2 = new HashMap<>();
            c2.put(id, -1.0); c2.put(rhs, 1.0);
            result.add(new LinearInequality(c2, 0.0));

        } else if (expr instanceof Constant) {
            Object val = ((Constant) expr).getValue();
            if (!(val instanceof Number)) return result;
            double k = ((Number) val).doubleValue();

            Map<Identifier, Double> c1 = new HashMap<>(); c1.put(id,  1.0);
            result.add(new LinearInequality(c1,  k));
            Map<Identifier, Double> c2 = new HashMap<>(); c2.put(id, -1.0);
            result.add(new LinearInequality(c2, -k));

            if (state != null) {
                for (Map.Entry<Identifier, double[]> e : state.unaryBounds.entrySet()) {
                    Identifier other = e.getKey();
                    if (isSpecial(other)) continue;
                    double lo = e.getValue()[0], hi = e.getValue()[1];
                    if (!Double.isInfinite(hi)) {
                        Map<Identifier, Double> cHi = new HashMap<>();
                        cHi.put(other, 1.0); cHi.put(id, -1.0);
                        result.add(new LinearInequality(cHi, hi - k));
                    }
                    if (!Double.isInfinite(lo)) {
                        Map<Identifier, Double> cLo = new HashMap<>();
                        cLo.put(id, 1.0); cLo.put(other, -1.0);
                        result.add(new LinearInequality(cLo, k - lo));
                    }
                }
            }

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            if (!(bin.getLeft() instanceof Identifier)) return result;
            Identifier rhs = (Identifier) bin.getLeft();
            if (isSpecial(rhs)) return result;
            if (!(bin.getRight() instanceof Constant)) return result;
            Object valObj = ((Constant) bin.getRight()).getValue();
            if (!(valObj instanceof Number)) return result;

            double k      = ((Number) valObj).doubleValue();
            boolean isAdd = bin.getOperator() instanceof AdditionOperator;
            boolean isSub = bin.getOperator() instanceof SubtractionOperator;
            if (!isAdd && !isSub) return result;

            double offset = isSub ? -k : k;
            Map<Identifier, Double> c1 = new HashMap<>();
            c1.put(id, 1.0); c1.put(rhs, -1.0);
            result.add(new LinearInequality(c1,  offset));
            Map<Identifier, Double> c2 = new HashMap<>();
            c2.put(id, -1.0); c2.put(rhs, 1.0);
            result.add(new LinearInequality(c2, -offset));
        }

        return result;
    }

    @Override
    public TwoVariablesPerInequality smallStepSemantics(
            ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    /** Adds the constraint expressed by a comparison expression to the abstract state; handles <=, <, >=, >, == by normalising all to one or two ≤ inequalities. */
    @Override
    public TwoVariablesPerInequality assume(
            ValueExpression expr,
            ProgramPoint src, ProgramPoint dest,
            SemanticOracle oracle) throws SemanticException {

        if (isBottom()) return this;

        List<LinearInequality> extracted = extractInequalities(expr);
        if (extracted.isEmpty()) return this;

        List<LinearInequality> newIneqs = new ArrayList<>(toInequalityList());
        for (LinearInequality ineq : extracted)
            if (!hasSpecial(ineq)) newIneqs.add(ineq);

        return TwoVariablesPerInequality.of(newIneqs);
    }

    /** Extracts one or two LinearInequality objects from a comparison expression,
     * normalising >=, >, == into <= form. */
    private List<LinearInequality> extractInequalities(ValueExpression expr) {
        List<LinearInequality> result = new ArrayList<>();
        if (!(expr instanceof BinaryExpression)) return result;

        BinaryExpression bin = (BinaryExpression) expr;
        Object op = bin.getOperator();

        SymbolicExpression left  = bin.getLeft();
        SymbolicExpression right = bin.getRight();
        if (op instanceof ComparisonGe || op instanceof ComparisonGt) {
            SymbolicExpression tmp = left; left = right; right = tmp;
            op = ComparisonLe.INSTANCE;
        }

        if (op instanceof ComparisonEq) {
            LinearInequality i1 = buildLe(left, right);
            LinearInequality i2 = buildLe(right, left);
            if (i1 != null && !hasSpecial(i1)) result.add(i1);
            if (i2 != null && !hasSpecial(i2)) result.add(i2);
            return result;
        }

        if (op instanceof ComparisonLe || op instanceof ComparisonLt) {
            LinearInequality ineq = buildLe(left, right);
            if (ineq != null && !hasSpecial(ineq)) result.add(ineq);
        }

        return result;
    }

    /** Builds a single LinearInequality for left <= right;
     * supports Identifier <= Identifier, Identifier <= Constant, Constant <= Identifier. */    private static LinearInequality buildLe(SymbolicExpression left, SymbolicExpression right) {
        if (left instanceof Identifier && right instanceof Identifier) {
            Identifier l = (Identifier) left, r = (Identifier) right;
            if (isSpecial(l) || isSpecial(r)) return null;
            Map<Identifier, Double> c = new HashMap<>();
            c.put(l, 1.0); c.put(r, -1.0);
            return new LinearInequality(c, 0.0);
        }
        if (left instanceof Identifier && right instanceof Constant) {
            Identifier l = (Identifier) left;
            if (isSpecial(l)) return null;
            Object val = ((Constant) right).getValue();
            if (!(val instanceof Number)) return null;
            Map<Identifier, Double> c = new HashMap<>();
            c.put(l, 1.0);
            return new LinearInequality(c, ((Number) val).doubleValue());
        }
        if (left instanceof Constant && right instanceof Identifier) {
            Identifier r = (Identifier) right;
            if (isSpecial(r)) return null;
            Object val = ((Constant) left).getValue();
            if (!(val instanceof Number)) return null;
            Map<Identifier, Double> c = new HashMap<>();
            c.put(r, -1.0);
            return new LinearInequality(c, -((Number) val).doubleValue());
        }
        return null;
    }

    /** Checks whether a comparison expression is provably SATISFIED, NOT_SATISFIED,
     * or UNKNOWN in the current abstract state. */
    @Override
    public Satisfiability satisfies(
            ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {

        if (isBottom()) return Satisfiability.BOTTOM;
        if (isTop())    return Satisfiability.UNKNOWN;

        List<LinearInequality> ineqs = extractInequalities(expr);
        if (ineqs.isEmpty()) return Satisfiability.UNKNOWN;

        for (LinearInequality ineq : ineqs) {
            Satisfiability s = checkOneSatisfied(ineq);
            if (s == Satisfiability.NOT_SATISFIED) return Satisfiability.NOT_SATISFIED;
            if (s == Satisfiability.UNKNOWN)       return Satisfiability.UNKNOWN;
        }
        return Satisfiability.SATISFIED;
    }

    /** Checks a single inequality against the stored unary or binary constraints,
     * returning SATISFIED, NOT_SATISFIED, or UNKNOWN. */
    private Satisfiability checkOneSatisfied(LinearInequality ineq) {
        if (ineq.varCount() == 1) {
            Identifier v = ineq.singleVar();
            double[] iv  = unaryBounds.get(v);
            if (iv == null) return Satisfiability.UNKNOWN;
            double coeff = ineq.coefficient(v);
            double bound = ineq.rhs / coeff;
            if (coeff > 0) {
                if (iv[1] <= bound + EPS) return Satisfiability.SATISFIED;
                if (iv[0] >  bound + EPS) return Satisfiability.NOT_SATISFIED;
            } else {
                if (iv[0] >= bound - EPS) return Satisfiability.SATISFIED;
                if (iv[1] <  bound - EPS) return Satisfiability.NOT_SATISFIED;
            }
            return Satisfiability.UNKNOWN;
        }
        if (ineq.varCount() == 2) {
            VarPair pair = VarPair.of(ineq);
            if (pair == null) return Satisfiability.UNKNOWN;
            List<LinearInequality> plane = binaryPlanes.get(pair);
            if (plane == null) return Satisfiability.UNKNOWN;

            if (planarEntails(plane, List.of(ineq), pair))
                return Satisfiability.SATISFIED;

            Map<Identifier, Double> negCoeffs = new HashMap<>();
            for (Map.Entry<Identifier, Double> e : ineq.coefficients.entrySet())
                negCoeffs.put(e.getKey(), -e.getValue());

            LinearInequality negated = new LinearInequality(negCoeffs, -ineq.rhs - EPS);
            if (planarEntails(plane, List.of(negated), pair))
                return Satisfiability.NOT_SATISFIED;

            return Satisfiability.UNKNOWN;
        }
        return Satisfiability.UNKNOWN;
    }

    /** Removes variable id from the abstract state (Definition 12): drops all binary planes
     * involving id and its unary bound; correct for complete systems by Proposition 6. */
    @Override
    public TwoVariablesPerInequality forgetIdentifier(Identifier id) throws SemanticException {
        if (isTop() || isBottom()) return this;

        Map<VarPair, List<LinearInequality>> newBinary = new HashMap<>();
        for (Map.Entry<VarPair, List<LinearInequality>> e : binaryPlanes.entrySet())
            if (!e.getKey().contains(id))
                newBinary.put(e.getKey(), e.getValue());

        Map<Identifier, double[]> newUnary = new HashMap<>(unaryBounds);
        newUnary.remove(id);

        if (newBinary.isEmpty() && newUnary.isEmpty()) return TOP;
        return new TwoVariablesPerInequality(false, false, newBinary, newUnary);
    }

    /** Removes all variables satisfying the predicate from the abstract state. */
    @Override
    public TwoVariablesPerInequality forgetIdentifiersIf(Predicate<Identifier> pred)
            throws SemanticException {
        if (isTop() || isBottom()) return this;

        Map<VarPair, List<LinearInequality>> newBinary = new HashMap<>();
        for (Map.Entry<VarPair, List<LinearInequality>> e : binaryPlanes.entrySet())
            if (!pred.test(e.getKey().x) && !pred.test(e.getKey().y))
                newBinary.put(e.getKey(), e.getValue());

        Map<Identifier, double[]> newUnary = new HashMap<>();
        for (Map.Entry<Identifier, double[]> e : unaryBounds.entrySet())
            if (!pred.test(e.getKey()))
                newUnary.put(e.getKey(), e.getValue());

        if (newBinary.isEmpty() && newUnary.isEmpty()) return TOP;
        return new TwoVariablesPerInequality(false, false, newBinary, newUnary);
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        if (isSpecial(id)) return false;
        return unaryBounds.containsKey(id)
                || binaryPlanes.keySet().stream().anyMatch(p -> p.contains(id));
    }

    @Override public TwoVariablesPerInequality pushScope(ScopeToken t) throws SemanticException { return this; }
    @Override public TwoVariablesPerInequality popScope(ScopeToken t)  throws SemanticException { return this; }

    /** Standard polyhedral widening lifted to planar projections (Section 7.1): keeps constraints
     * from this that are still entailed by other; drops unary bounds that have grown. */

    @Override
    public TwoVariablesPerInequality widening(TwoVariablesPerInequality other)
            throws SemanticException {
        if (isBottom()) return other;
        if (other.isTop() || isTop()) return TOP;

        Map<VarPair, List<LinearInequality>> newBinary = new HashMap<>();

        for (Map.Entry<VarPair, List<LinearInequality>> e : binaryPlanes.entrySet()) {
            VarPair pair = e.getKey();
            List<LinearInequality> thisPlane  = e.getValue();
            List<LinearInequality> otherPlane = other.binaryPlanes.get(pair);
            if (otherPlane == null) continue;

            List<LinearInequality> kept = new ArrayList<>();
            for (LinearInequality ineq : thisPlane)
                if (planarEntails(otherPlane, List.of(ineq), pair))
                    kept.add(ineq);

            if (kept.size() > MAX_INEQS_PER_PLANE)
                kept = limitToMaxInequalities(kept, pair);

            if (!kept.isEmpty())
                newBinary.put(pair, kept);
        }

        Map<Identifier, double[]> newUnary = new HashMap<>();
        for (Map.Entry<Identifier, double[]> e : unaryBounds.entrySet()) {
            Identifier v     = e.getKey();
            double[] thisIv  = e.getValue();
            double[] otherIv = other.unaryBounds.get(v);
            if (otherIv == null) continue;

            double newLo = (otherIv[0] < thisIv[0] - EPS) ? Double.NEGATIVE_INFINITY : thisIv[0];
            double newHi = (otherIv[1] > thisIv[1] + EPS) ? Double.POSITIVE_INFINITY : thisIv[1];

            if (!Double.isInfinite(newLo) || !Double.isInfinite(newHi))
                newUnary.put(v, new double[]{ newLo, newHi });
        }

        if (newBinary.isEmpty() && newUnary.isEmpty()) return TOP;
        return new TwoVariablesPerInequality(false, false, newBinary, newUnary);
    }

    /** Reduces a projection to at most MAX_INEQS_PER_PLANE by repeatedly removing
     * the inequality with the smallest angular gap to its neighbours (Section 7.2). */
    private List<LinearInequality> limitToMaxInequalities(
            List<LinearInequality> ineqs, VarPair pair) {

        Identifier x = pair.x, y = pair.y;
        List<LinearInequality> working = new ArrayList<>(ineqs);

        while (working.size() > MAX_INEQS_PER_PLANE) {
            int minIdx = 0;
            double minGap = Double.MAX_VALUE;
            int n = working.size();
            for (int i = 0; i < n; i++) {
                double prev = angleOf(working.get((i-1+n) % n), x, y);
                double next = angleOf(working.get((i+1)   % n), x, y);
                double gap  = (next - prev + 2*Math.PI) % (2*Math.PI);
                if (gap < minGap) { minGap = gap; minIdx = i; }
            }
            working.remove(minIdx);
        }
        return working;
    }

    /** Collects all inequalities (binary planes + unary bounds) into a flat deduplicated list. */
    public List<LinearInequality> toInequalityList() {
        Set<LinearInequality> seen = new LinkedHashSet<>();

        for (List<LinearInequality> plane : binaryPlanes.values())
            seen.addAll(plane);

        for (Map.Entry<Identifier, double[]> e : unaryBounds.entrySet()) {
            Identifier id = e.getKey();
            double lo = e.getValue()[0], hi = e.getValue()[1];
            if (!Double.isInfinite(hi)) {
                Map<Identifier, Double> c = new HashMap<>(); c.put(id, 1.0);
                seen.add(new LinearInequality(c, hi));
            }
            if (!Double.isInfinite(lo)) {
                Map<Identifier, Double> c = new HashMap<>(); c.put(id, -1.0);
                seen.add(new LinearInequality(c, -lo));
            }
        }

        return new ArrayList<>(seen);
    }

    @Override
    public String toString() {
        if (isTop)    return "⊤";
        if (isBottom) return "⊥";
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        for (LinearInequality ineq : toInequalityList())
            sj.add(ineq.toString());
        return sj.toString();
    }

    /** Computes the angle theta of an inequality in the {x,y} plane (Definition 6): atan2(a/norm, -b/norm) for ax+by<=c. */
    static double angleOf(LinearInequality ineq, Identifier x, Identifier y) {
        double a = ineq.coefficient(x), b = ineq.coefficient(y);
        double norm = Math.sqrt(a*a + b*b);
        if (norm < EPS) return 0.0;
        return Math.atan2(a/norm, -b/norm);
    }

    /** Computes the angle theta of an inequality in the {x,y} plane (Definition 6):
     * atan2(a/norm, -b/norm) for ax+by<=c. */
    static boolean sameAngle(LinearInequality i1, LinearInequality i2,
                             Identifier x, Identifier y) {
        return Math.abs(angleOf(i1, x, y) - angleOf(i2, x, y)) < EPS;
    }

    /** Returns the anti-clockwise angular difference (next - prev) modulo 2 PI. */
    private static double angleDiff(LinearInequality next, LinearInequality prev,
                                    Identifier x, Identifier y) {
        return (angleOf(next, x, y) - angleOf(prev, x, y) + 2*Math.PI) % (2*Math.PI);
    }

    static final class VarPair {
        final Identifier x, y;

        VarPair(Identifier a, Identifier b) {
            if (a.getName().compareTo(b.getName()) <= 0) { x = a; y = b; }
            else                                          { x = b; y = a; }
        }

        static VarPair of(LinearInequality ineq) {
            if (ineq == null || ineq.varCount() != 2) return null;
            Iterator<Identifier> it = ineq.vars().iterator();
            return new VarPair(it.next(), it.next());
        }

        boolean contains(Identifier id) { return x.equals(id) || y.equals(id); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof VarPair)) return false;
            VarPair p = (VarPair) o;
            return x.equals(p.x) && y.equals(p.y);
        }
        @Override public int hashCode() { return 31*x.hashCode() + y.hashCode(); }
        @Override public String toString() { return "{"+x.getName()+","+y.getName()+"}"; }
    }

    private static class ExtremeResult {
        final List<double[]> vertices = new ArrayList<>();
        final List<double[]> rays     = new ArrayList<>();
    }

    /** A single linear inequality of the form a1*id1 + a2*id2 + ... <= rhs,
     * where only id1 and id2 may be non-special variables.
     */
    public static final class LinearInequality {

        public final Map<Identifier, Double> coefficients;
        public final double rhs; // the inequality is coeffs ≤ rhs (rhs stands for the right-hand side)

        public LinearInequality(Map<Identifier, Double> coefficients, double rhs) {
            double normSq = 0.0;
            for (Double val : coefficients.values()) normSq += val * val;
            double norm = Math.sqrt(normSq);

            Map<Identifier, Double> clean = new LinkedHashMap<>();
            if (norm > EPS) {
                for (Map.Entry<Identifier, Double> e : coefficients.entrySet())
                    if (Math.abs(e.getValue()) >= EPS)
                        clean.put(e.getKey(), e.getValue() / norm);
                this.rhs = rhs / norm;
            } else {
                this.rhs = rhs;
            }
            this.coefficients = Collections.unmodifiableMap(clean);
        }

        public Set<Identifier>  vars()                     { return coefficients.keySet(); }
        public int              varCount()                 { return coefficients.size(); }
        public double           coefficient(Identifier id) { return coefficients.getOrDefault(id, 0.0); }
        public Identifier       singleVar()                { return coefficients.keySet().iterator().next(); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LinearInequality)) return false;
            LinearInequality that = (LinearInequality) o;
            if (Math.abs(rhs - that.rhs) >= EPS) return false;
            if (coefficients.size() != that.coefficients.size()) return false;
            for (Map.Entry<Identifier, Double> e : coefficients.entrySet()) {
                Double v = that.coefficients.get(e.getKey());
                if (v == null || Math.abs(e.getValue() - v) >= EPS) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (Map.Entry<Identifier, Double> e : coefficients.entrySet())
                h += e.getKey().hashCode()
                        ^ Double.hashCode(Math.round(e.getValue() * 1e6) / 1e6);
            return h ^ Double.hashCode(Math.round(rhs * 1e6) / 1e6);
        }

        @Override
        public String toString() {
            if (coefficients.isEmpty())
                return "0 <= " + Math.round(rhs * 1e4) / 10000.0;

            double scale = 1.0;
            for (Double val : coefficients.values()) {
                if (Math.abs(val) > EPS) { scale = Math.abs(val); break; }
            }

            StringBuilder sb = new StringBuilder();
            boolean first = true;

            List<Map.Entry<Identifier, Double>> entries =
                    new ArrayList<>(coefficients.entrySet());
            entries.sort(Comparator.comparing(e -> e.getKey().getName()));

            for (Map.Entry<Identifier, Double> e : entries) {
                double v       = e.getValue() / scale;
                double rounded = Math.round(v * 10000.0) / 10000.0;
                if (rounded == 0) continue;

                if (!first) sb.append(rounded > 0 ? " + " : " - ");
                else if (rounded < 0) sb.append("-");

                double absV = Math.abs(rounded);
                if (Math.abs(absV - 1.0) > EPS) {
                    if (absV == Math.floor(absV)) sb.append((int) absV).append("*");
                    else                          sb.append(absV).append("*");
                }
                sb.append(e.getKey().getName());
                first = false;
            }

            double roundedRhs = Math.round((rhs / scale) * 10000.0) / 10000.0;
            if (roundedRhs == Math.floor(roundedRhs))
                sb.append(" <= ").append((int) roundedRhs);
            else
                sb.append(" <= ").append(roundedRhs);

            return sb.toString();
        }
    }
}