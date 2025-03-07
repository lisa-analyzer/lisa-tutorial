package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

public class Intervalles implements BaseNonRelationalValueDomain<Intervalles>  {
    public static final Intervalles TOP = new Intervalles(Integer.MIN_VALUE, Integer.MAX_VALUE);
    private final int min, max;

    public Intervalles(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Intervalles lubAux(Intervalles intervalles) throws SemanticException {
        return new Intervalles(Math.min(intervalles.min, min), Math.max(intervalles.max, max));
    }

    @Override
    public boolean lessOrEqualAux(Intervalles intervalles) throws SemanticException {
        return false;
    }

    @Override
    public Intervalles top() {
        return TOP;
    }

    private static final Intervalles BOTTOM = new Intervalles(Integer.MAX_VALUE, Integer.MIN_VALUE);

    @Override
    public Intervalles bottom() {
        return BOTTOM;
    }

    @Override
    public StructuredRepresentation representation() {
        return new StringRepresentation("["+min+".."+max+"]");
    }
}
