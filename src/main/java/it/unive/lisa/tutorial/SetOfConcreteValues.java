package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.HashSet;
import java.util.Set;

public class SetOfConcreteValues implements BaseNonRelationalValueDomain<SetOfConcreteValues> {
    private Set<Integer> values;
    private boolean isTop;
    private static SetOfConcreteValues top, bottom;

    static {
        top = new SetOfConcreteValues(true);
        bottom = new SetOfConcreteValues(false);
    }

    private SetOfConcreteValues(boolean isTop) {
        this.isTop = isTop;
    }

    private SetOfConcreteValues(Set<Integer> values) {
        this.values = values;
    }

    public SetOfConcreteValues(int i) {
        values = new HashSet<>();
        values.add(Integer.valueOf(i));
    }


    @Override
    public SetOfConcreteValues lubAux(SetOfConcreteValues other) throws SemanticException {
        if(this.isBottom())
            return other;
        if(other.isBottom())
            return this;
        if(this.isTop() || other.isTop())
            return this.top();
        Set<Integer> s = new HashSet<>(this.values);
        s.addAll(other.values);
        return new SetOfConcreteValues(s);

    }

    @Override
    public boolean lessOrEqualAux(SetOfConcreteValues other) throws SemanticException {
        return false;
        //check if this is a subset of other TODO
    }

    @Override
    public SetOfConcreteValues top() {
        return top;
    }

    @Override
    public SetOfConcreteValues bottom() {
        return bottom;
    }

    @Override
    public StructuredRepresentation representation() {
        if(isTop) return new StringRepresentation("T");
        if(values.isEmpty()) return new StringRepresentation("_|_");
        String value = "";
        for(Integer i : values)
            value += String.valueOf(i) + ";";
        return new StringRepresentation(value);
    }
}
