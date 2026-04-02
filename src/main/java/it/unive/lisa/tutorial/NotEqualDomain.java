package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.FunctionalLattice;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.program.cfg.statement.comparison.NotEqual;
import it.unive.lisa.symbolic.value.*;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonNe;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

public class NotEqualDomain extends FunctionalLattice<NotEqualDomain, Identifier, NotEqualDomain.SetOfIdentifiers> implements ValueDomain<NotEqualDomain> {
    public NotEqualDomain() {
        super(new SetOfIdentifiers(Collections.emptySet(), true));
    }

    private NotEqualDomain(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        super(lattice, function);
    }

    @Override
    public SetOfIdentifiers stateOfUnknown(Identifier key) {
        return new SetOfIdentifiers(Collections.emptySet(), true);
    }

    @Override
    public NotEqualDomain mk(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        return new NotEqualDomain(lattice, function);
    }

    @Override
    public NotEqualDomain top() {
        return new NotEqualDomain(lattice.top(), null);
    }

    @Override
    public NotEqualDomain bottom() {
        return new NotEqualDomain(lattice.bottom(), null);
    }

    @Override
    public NotEqualDomain assign(Identifier id, ValueExpression expression, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        NotEqualDomain result =  this.forgetIdentifier(id);
        if(expression instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expression;
            it.unive.lisa.symbolic.value.operator.binary.BinaryOperator op
                    = bin.getOperator();
            if(op instanceof AdditionOperator || op instanceof SubtractionOperator) {
                if(bin.getLeft() instanceof Identifier && bin.getRight() instanceof Constant) {
                    Constant c = (Constant) bin.getRight();
                    if(c.getValue() instanceof Integer && ((Integer) c.getValue()) != 0) {
                        if( ! id.equals(bin.getLeft())) {
                            result = result.putState(id, new SetOfIdentifiers(Collections.singleton((Identifier) bin.getLeft()), false));
                            SetOfIdentifiers value = result.getState((Identifier) bin.getLeft());
                            if(value.isTop())
                                value = new SetOfIdentifiers(Collections.singleton(id), true);
                            else {
                                Set<Identifier> val = new HashSet<>(value.elements);
                                val.add(id);
                                value = new SetOfIdentifiers(val, false);
                            }
                            result = result.putState((Identifier) bin.getLeft(), value);
                        }
                    }
                }
            }

        }
        return result;
    }

    @Override
    public NotEqualDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return this;
    }

    @Override
    public NotEqualDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle) throws SemanticException {
        NotEqualDomain ret = this;
        if(expression instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expression;
            Operator op = bin.getOperator();
            if(op instanceof ComparisonNe && bin.getLeft() instanceof Identifier && bin.getRight() instanceof Identifier) {
                Identifier left = (Identifier) bin.getLeft();
                Identifier right = (Identifier) bin.getRight();
                SetOfIdentifiers value = ret.getState((Identifier) bin.getLeft());
                if(value.isTop())
                    value = new SetOfIdentifiers(Collections.singleton(right), true);
                else {
                    Set<Identifier> val = new HashSet<>(value.elements);
                    val.add(right);
                    value = new SetOfIdentifiers(val, false);
                }
                ret = ret.putState((Identifier) bin.getLeft(), value);

                value = ret.getState((Identifier) bin.getRight());
                if(value.isTop())
                    value = new SetOfIdentifiers(Collections.singleton(left), true);
                else {
                    Set<Identifier> val = new HashSet<>(value.elements);
                    val.add(left);
                    value = new SetOfIdentifiers(val, false);
                }
                ret = ret.putState((Identifier) bin.getRight(), value);
            }

        }
        return ret;
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        return function.containsKey(id);
    }

    @Override
    public NotEqualDomain forgetIdentifier(Identifier id) throws SemanticException {
        if(this.isTop())
            return this;
        NotEqualDomain ret = this;
        if(this.function.containsKey(id)) {
            ret = ret.putState(id, lattice.top());
        }
        for(Identifier i : this.function.keySet()) {
            if(this.function.get(i).contains(id)) {
                Set<Identifier> value = new HashSet<>(this.getState(i).elements);
                value.remove(id);
                ret = ret.putState(i, new SetOfIdentifiers(value, value.isEmpty()));
            }
        }
        //TODO remove from all the codomain values
        return ret;
    }

    @Override
    public NotEqualDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        return this;
    }

    @Override
    public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if(this.isBottom())
            return Satisfiability.BOTTOM;
        else return Satisfiability.UNKNOWN;
    }

    @Override
    public NotEqualDomain pushScope(ScopeToken token) throws SemanticException {
        return this;
    }

    @Override
    public NotEqualDomain popScope(ScopeToken token) throws SemanticException {
        return this;
    }

    static class SetOfIdentifiers extends InverseSetLattice<SetOfIdentifiers, Identifier> {
        /**
         * Builds the lattice.
         *
         * @param elements the elements that are contained in the lattice
         * @param isTop    whether or not this is the top or bottom element of the
         *                 lattice, valid only if the set of elements is empty
         */
        public SetOfIdentifiers(Set<Identifier> elements, boolean isTop) {
            super(elements, isTop);
        }

        @Override
        public SetOfIdentifiers mk(Set<Identifier> set) {
            return new SetOfIdentifiers(set, set.isEmpty());
        }

        @Override
        public SetOfIdentifiers top() {
            return this.mk(Collections.emptySet());
        }

        @Override
        public SetOfIdentifiers bottom() {
            return new SetOfIdentifiers(Collections.emptySet(), false);
        }
    }


}
