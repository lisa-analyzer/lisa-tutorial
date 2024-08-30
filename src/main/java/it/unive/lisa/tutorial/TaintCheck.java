package it.unive.lisa.tutorial;

import it.unive.lisa.program.annotations.Annotation;
import it.unive.lisa.program.annotations.matcher.AnnotationMatcher;
import it.unive.lisa.program.annotations.matcher.BasicAnnotationMatcher;

public class TaintCheck
		/*implements
		// a semantic check is an iterator of CFGs that has access
		// to the states computed by the analysis
		SemanticCheck<
				// the type parameter for the semantic checks is
				// the abstract state used for the analysis
				SimpleAbstractState<
						PointBasedHeap,
						ValueEnvironment<Taint>,
						TypeEnvironment<InferredTypes>>> */{

	/**
	 * The annotation used to mark sinks where tainted information should not flow.
	 */
	public static final Annotation SINK_ANNOTATION = new Annotation("lisa.taint.Sink");

	/**
	 * An {@link AnnotationMatcher} for {@link #SINK_ANNOTATION}. Annotation matchers are just utility objects that *
	 * allow for conditional matching of annotations based on names, parameters, ...
	 */
	public static final AnnotationMatcher SINK_MATCHER = new BasicAnnotationMatcher(SINK_ANNOTATION);

	// This method is called for each statement of each analyzed CFG
	/*@Override
	public boolean visit(
			CheckToolWithAnalysisResults<SimpleAbstractState<PointBasedHeap, ValueEnvironment<Taint>, TypeEnvironment<InferredTypes>>> tool,
			CFG graph,
			Statement node) {
		// we try to detect calls with a sink parameter for which the analysis determined
		// that there might be tainted information reaching that parameter

		if (!(node instanceof UnresolvedCall))
			// if it is a statement that it is not a call, then we don't care
			// because it cannot have parameters annotated as sinks
			return true;

		UnresolvedCall call = (UnresolvedCall) node;
		try {
			// we get the taint analysis results mapped on the CFG containing the call that we want investigate
			for (var result : tool.getResultOf(call.getCFG())) {
				// we resolve the call, i.e. we ensure that call has been correctly processed by the analysis
				Call res = tool.getResolvedVersion(call, result);
				if (res == null)
					// if the call has not been resolved, we cannot inspect its targets to find the annotations
					return true;

				for (CodeMember target : ((ResolvedCall) res).getTargets()) {
					// we check if the call parameters are annotated as sinks
					Parameter[] parameters = target.getDescriptor().getFormals();
					for (int par = 0; par < parameters.length; par++)
						if (parameters[par].getAnnotations().contains(SINK_MATCHER) && mightBeTainted(result, call, par))
							// tainted data might flow into the sink: we report a warning
							tool.warnOn(call, "The value passed for the "
									+ StringUtilities.ordinal(par + 1)
									+ " parameter of this call may be tainted, and it reaches the sink at parameter '"
									+ parameters[par].getName()
									+ "' of "
									+ res.getFullTargetName());
				}
			}
		} catch (SemanticException e) {
			System.err.println("Cannot check " + node);
			e.printStackTrace(System.err);
		}

		return true;
	}

	private static boolean mightBeTainted(
			AnalyzedCFG<
					SimpleAbstractState<
							PointBasedHeap,
							ValueEnvironment<Taint>,
							TypeEnvironment<InferredTypes>>> result,
			UnresolvedCall call,
			int parIndex) throws SemanticException {

	}*/
}