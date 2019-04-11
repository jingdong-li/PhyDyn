package phydyn.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.TraitSet;
import beast.util.TreeParser;
import phydyn.model.PopModel;


/**
 * @author Igor Siveroni
 * PhyDyn analysis class. Considers the following analyses types:
 * - Likelihood, modelmcmc, treemcmc.
 * One of the main objectives of this class is to produce a Runnable object that can be 
 * used to generate a BEAST XML file.
 */


public abstract class Analysis {
	
	public enum AType { LH, FTMCMC, TMCMC};
	
	public AType analysisType;
	
	// This is a PhyDyn analysis, so we need a phydyn popmodel
	public PopModel popModel;
	public String popModelName;
	
	// Yes, we always need a tree
	protected TreeParser tree;
	protected int numTips;
	protected String[] tipNames;
	protected Node[] tipNodes;
	protected double treeHeight;
	protected double mrTipDate; // most recent date 
	
	protected TaxonSet taxa;	
	// Date Trait
	protected TraitSet dateTrait; // use Beast object to store info	
	// Type Trait
	protected TraitSet typeTrait; 
	

	
	public static Analysis createAnalysis(AType t, PopModel m) {
		switch (t) {
		case LH:
			return new LikelihoodAnalysis(m);
		case FTMCMC:
		case TMCMC:
			return null;
		default:
			return null;
		}
	}
	
	public Analysis(PopModel m) {
		popModel = m;
		popModelName = m.getName();
	}

	// Create tree from newick string
	public void addTree(String newick, Boolean adjust) {
		 /**
	     * @param newick                a string representing a tree in newick format
	     * @param adjustTipHeights      true if the tip heights should be adjusted to 0 (i.e. contemporaneous) after reading in tree.
	     * @param allowSingleChildNodes true if internal nodes with single children are allowed
	     * @param isLabeled             true if nodes are labeled with taxa labels
	     * @param offset                if isLabeled == false and node labeling starts with x
	     *                              then offset should be x. When isLabeled == true offset should
	     *                              be 1 as by default.
	     */	   
		tree = new TreeParser(newick, adjust, false, true, 0);
		tree.setID(popModelName+".t");
		
		taxa = tree.m_taxonset.get();
		taxa.setID(popModelName+".taxa");
		
		// taxon set is set a the end using tips labels - can we extract it?
		// tree.m_taxonset.get().setID("oeee");  ;
		Node root = tree.getRoot();
		//System.out.println("root nr: "+root.getNr());
		System.out.println("root height: "+root.getHeight());
			
		/* Visit Nodes  */
		Node[] nodes = tree.listNodesPostOrder(null, null);
		/* Node[] nodes = tree.getNodesAsArray(); */
		/* List<Node> nodes = root.getAllChildNodes(); */
		numTips = 0;
		List<String> ids = new ArrayList<String>();
		List<Node> leaves = new ArrayList<Node>();
		for(Node node: nodes) {
			if (node.isLeaf()) {
				//System.out.print(node.isLeaf() ? "Leaf     " : "Internal ");
				ids.add(node.getID());
				leaves.add(node);
				//System.out.println(node.getHeight());
				//System.out.print("Nr: "+node.getNr()+ " id:"+node.getID()+" h:"+node.getHeight());
				//System.out.println(" toParent " + node.getLength()+" meta: "+node.metaDataString);
			}
		}			
		numTips = ids.size();
		tipNames = new String[numTips];
		tipNodes = new Node[numTips];
		ids.toArray(tipNames);
		leaves.toArray(tipNodes);
		ids.clear(); leaves.clear();
		// Dates
		treeHeight = root.getHeight();
		System.out.println("new num tips = "+numTips);
		
		
	}
	
	// Create a date trait using information from the tree tips.
	public void addDateTrait() {
		dateTrait = new TraitSet();
		if (popModel.hasEndTime()) {
			mrTipDate = popModel.getEndTime();
			popModel.unsetEndTime();
	    } else {
	    	mrTipDate = treeHeight;
	    }
		// date is extracted from tipname;
		String datePairs = tipNames[0]+"="+(mrTipDate-tipNodes[0].getHeight());
		for(int i = 1; i < numTips; i++) {
			datePairs += ","+tipNames[i]+"="+(mrTipDate-tipNodes[i].getHeight());
		}	
		
		dateTrait.traitNameInput.setValue(TraitSet.DATE_TRAIT, dateTrait);
		dateTrait.traitsInput.setValue(datePairs, dateTrait);
		dateTrait.taxaInput.setValue(taxa, dateTrait);
		dateTrait.initAndValidate();
		dateTrait.setID(popModelName+".dates");
		// add date trait to tree
		tree.setDateTrait(dateTrait);
	}
	
	// TypeTrait to be added to stlikelihood. type extracted from tree tips
	public void addTypeTrait() {
		typeTrait = new TraitSet();
		
		String[] splits = tipNames[0].split("_");
		String stateName = splits[splits.length-1];
		String typePairs = tipNames[0]+"="+stateName;
		for(int i = 1; i < numTips; i++) {
			splits = tipNames[i].split("_");
			stateName = splits[splits.length-1];
			typePairs += ","+tipNames[i]+"="+stateName;
		}	
		typeTrait.traitNameInput.setValue("type-trait", typeTrait);
		typeTrait.traitsInput.setValue(typePairs, typeTrait);
		typeTrait.taxaInput.setValue(taxa, typeTrait);
		typeTrait.initAndValidate();
		typeTrait.setID(popModelName+".types");
		System.out.println("superclass add type");
		
	}

	
	// create a fully-connected Runnable object
	public abstract beast.core.Runnable getRunnableObject();
	
		
	
}
