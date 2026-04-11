package it.unive.lisa.tutorial.src.it.unive.lisa.tutorial;

import it.unive.lisa.AnalysisException;
import it.unive.lisa.DefaultConfiguration;
import it.unive.lisa.LiSA;
import it.unive.lisa.analysis.heap.pointbased.FieldSensitivePointBasedHeap;
import it.unive.lisa.conf.LiSAConfiguration;
import it.unive.lisa.conf.LiSAConfiguration.GraphType;
import it.unive.lisa.imp.IMPFrontend;
import it.unive.lisa.imp.ParsingException;
import it.unive.lisa.program.Program;
import org.junit.Test;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;


public class ExtendedSignsAndTVPICartesianTest {

    @Test
    public void testCartesian() throws ParsingException, AnalysisException {
        Program program = IMPFrontend.processFile("inputs/extendedsignsandtvpicartesian.imp");
        LiSAConfiguration conf = new DefaultConfiguration();
        conf.workdir = "outputs/extendedsignsandtvpicartesian";
        conf.analysisGraphs = GraphType.HTML;
        var twoVariablesInequality = TwoVariablesPerInequality.TOP;
        var extendedSigns = new ValueEnvironment<>(new ExtendedSigns());
        conf.abstractState = DefaultConfiguration.simpleState(
                new FieldSensitivePointBasedHeap(),
                new ExtendedSignsAndTVPICartesian(
                        twoVariablesInequality,
                        extendedSigns
                ),
                DefaultConfiguration.defaultTypeDomain());
        LiSA lisa = new LiSA(conf);
        lisa.run(program);
    }
}