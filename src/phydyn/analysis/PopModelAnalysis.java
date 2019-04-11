package phydyn.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import phydyn.model.PopModel;
import phydyn.model.PopModelBailLexer;
import phydyn.model.PopModelParserErrorStrategy;
import phydyn.model.parser.PopModelLexer;
import phydyn.model.parser.PopModelParser;
import phydyn.model.parser.PopModelParser.AnalysisSpecContext;
import phydyn.run.XMLGenerator;

// TODO: Get the new Analysis treatment

public class PopModelAnalysis {
	
	public String analysisStr;
	private AnalysisSpecContext analysisCtx;
	public PopModel popModel;
	public XMLGenerator xmlGenerator; // temporary
	public List<Parameter> parameters;
	public List<Prior> priors;
	public List<Operator> operators;
	private HashMap<String,String> paramMap;
	
	// state related
	private int chainLength, storeEvery;
	
	
	
	public PopModelAnalysis(String aStr, PopModel popModel, XMLGenerator xmlgen) {
		analysisStr = aStr;
		this.popModel = popModel;
		this.xmlGenerator = xmlgen;
		CodePointCharStream  input = CharStreams.fromString( analysisStr );
		//ANTLRInputStream input = new ANTLRInputStream(equationsStringInput.get());
		try {
			PopModelLexer lexer = new PopModelBailLexer(input); 
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			PopModelParser parser = new PopModelParser(tokens);
			parser.setErrorHandler(new PopModelParserErrorStrategy());			
			analysisCtx = parser.analysisSpec();
		} catch (Exception e) {
			//System.out.println( "Error while parsing analysis: "+analysisStr);
			System.out.println( "Error while parsing analysis.");
			throw new IllegalArgumentException("Parsing error");
		}		
		// first pass: traverse syntax tree and create analysis objects
		PMAnalysisChecker checker = new PMAnalysisChecker();
		parameters = new ArrayList<Parameter>();
		priors = new ArrayList<Prior>();
		operators = new ArrayList<Operator>();
		Boolean error = checker.check(analysisCtx,popModel,parameters,priors,operators);
		if (error) {
			System.out.println("Error with analysis definition");
			throw new IllegalArgumentException("Parsing error");
		} 
		// Must check that parameter exists (in popModel)
		paramMap = buildParamMap();
		
		// second pass: link objects. extra checks.
		
		// defaults
		chainLength = 200;
		storeEvery = 50;
	}
	
	private HashMap<String,String> buildParamMap() {
		HashMap<String, String> map = new HashMap<String, String>();
		for (Parameter p : parameters) {
			map.put(p.name, p.id);
			// parameters already checked in check() traversal
		}
		return map;
	}
	
	public String getParamID(String paramName) {
		return paramMap.get(paramName);
	}
	
	@Override
	public String toString() {
		String s = "model-analysys = {\n";
		s += analysisStr + "\n";
		s += "}";
		return s;
	}
	
	private void writeMaps(XMLFileWriter writer) throws IOException {
		writer.EOL();
		writer.tabAppend("<map name=\"Uniform\">beast.math.distributions.Uniform</map>\n");
		writer.tabAppend("<map name=\"LogNormal\">beast.math.distributions.LogNormalDistributionModel</map>\n");
		writer.tabAppend("<map name=\"Normal\">beast.math.distributions.Normal</map>\n");
		writer.tabAppend("<map name=\"prior\">beast.math.distributions.Prior</map>\n");
		writer.EOL();
		//<map name="Exponential">beast.math.distributions.Exponential</map>
		//<map name="Beta">beast.math.distributions.Beta</map>
		//<map name="Gamma">beast.math.distributions.Gamma</map>
		//<map name="LaplaceDistribution">beast.math.distributions.LaplaceDistribution</map>
		//<map name="InverseGamma">beast.math.distributions.InverseGamma </map>
	}
	
	public String writeXML(XMLFileWriter writer) throws IOException {
		writeMaps(writer);
		String xmlRun = "<run id=\"mcmc\" spec=\"MCMC\" chainLength=\"*cl*\">\n";
		String s = xmlRun.replace("*cl*",Integer.toString(chainLength));
		writer.tabAppend(s);
		writer.tab();
		writeState(writer);
		writeDistribution(writer);
		writeOperators(writer);
		writeLoggers(writer);
		writer.untab();
		writer.tabAppend("</run>\n");
		return "analysisID";
	}
	
	public String writeState(XMLFileWriter writer) throws IOException {
		String xmlState = "<state id=\"state\" storeEvery=\"*se*\">\n";
		String s = xmlState.replace("*se*",Integer.toString(storeEvery));
		writer.tabAppend(s);
		writer.tab();
		for(int i=0;i < parameters.size(); i++) {
			parameters.get(i).writeXML(writer, popModel);
		}
		writer.untab();
		writer.tabAppend("</state>\n");
		return "stateID";
	}
	
	public String writeDistribution(XMLFileWriter writer) throws IOException {
		writer.tabAppend("<distribution id=\"posterior\" spec=\"util.CompoundDistribution\">\n");
		writer.tab();
		writePriors(writer);
		// xmlGenerator.writeLikelihoodODE(writer);
		writer.untab();
		writer.tabAppend("</distribution>\n");
		return "posterior";
	}
	
	public String writePriors(XMLFileWriter writer) throws IOException {
		writer.tabAppend("<distribution id=\"prior\" spec=\"util.CompoundDistribution\">\n");
		writer.tab();
		for(int i=0; i < priors.size(); i++) {
			priors.get(i).writeXML(writer);
		}
		
		writer.untab();
		writer.tabAppend("</distribution>\n");
		return "prior";
	}
	
	public void writeOperators(XMLFileWriter writer) throws IOException {
		for(int i=0; i<operators.size(); i++) {
			operators.get(i).writeXML(writer);
		}
	}
	

	public void writeLoggers(XMLFileWriter writer) throws IOException {
		// screen log
		writer.tabAppend("<logger id=\"screenlog\" logEvery=\"10\">\n");
		writer.tab();
		writer.tabAppend("<log idref=\"posterior\"/>\n");
		writer.tabAppend("<log idref=\"stlh\"/>\n");
		for(int i=0; i < parameters.size(); i++) {
			writer.tabAppend("<log idref=\"" + parameters.get(i).id + "\"/>\n");
		}
		writer.untab();
		writer.tabAppend("</logger>\n");
		// file log
		String s = "<logger id=\"tracelog\" fileName=\"$(filebase).log\" logEvery=\"10\" ";
		s += "model=\"@posterior\" sanitiseHeaders=\"true\" sort=\"smart\">\n";
		writer.tabAppend(s);		
		writer.tab();
		writer.tabAppend("<log idref=\"posterior\"/>\n");
		writer.tabAppend("<log idref=\"stlh\"/>\n");
		for(int i=0; i < parameters.size(); i++) {
			writer.tabAppend("<log idref=\"" + parameters.get(i).id + "\"/>\n");
		}
		writer.untab();
		writer.tabAppend("</logger>\n");
	}

	
	
	
}
