package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.FunctionalLattice;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class NotEqualDomain extends FunctionalLattice<NotEqualDomain, Identifier, NotEqualDomain.SetOfIdentifiers> implements ValueDomain<NotEqualDomain> {
    public NotEqualDomain(SetOfIdentifiers lattice) {
        super(lattice);
    }

    public NotEqualDomain(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
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
        return this;
    }

    @Override
    public NotEqualDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return this;
    }

    @Override
    public NotEqualDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle) throws SemanticException {
        return this;
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        return function.containsKey(id);
    }

    @Override
    public NotEqualDomain forgetIdentifier(Identifier id) throws SemanticException {
        NotEqualDomain ret = this;
        if(this.function.containsKey(id)) {
            ret = ret.putState(id, lattice.top());
        }
        //TODO remove from all the codomain values
        return ret;
    }

    @Override
    public NotEqualDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        return null;
    }

    @Override
    public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return null;
    }

    @Override
    public NotEqualDomain pushScope(ScopeToken token) throws SemanticException {
        return null;
    }

    @Override
    public NotEqualDomain popScope(ScopeToken token) throws SemanticException {
        return null;
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
