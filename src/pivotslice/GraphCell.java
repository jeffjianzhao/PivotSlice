package pivotslice;

import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;

import pivotslice.GraphCanvas.VisualStatus;


import com.microsoft.research.Publication;

public class GraphCell implements Serializable {
	private static final long serialVersionUID = -3942269736801923836L;
	
	private static final int layoutIteration = 40;
	private static final double dispRatio = 0.4;
    private static final double EPSILON = 0.000001D;
	private static final double startTemp = 0.05;
	
	public transient static PivotSlice rootFrame;
	public static HashMap<EdgeVisual.Edge, EdgeVisual> edgeVisuals = new HashMap<EdgeVisual.Edge, EdgeVisual>();
	
	public boolean xAggr, yAggr;
	public boolean matrixLayout;
	public int gridx, gridy;
	public boolean isScattered;
	
	public Rectangle2D.Double bounds;
	public int dupNode;
	public HashSet<NodeVisual> dupNodeVisuals = new HashSet<NodeVisual>();
	public int edgeTotal, edgeCrossIn, edgeCrossOut;
	
	public HashSet<Publication> publications = new HashSet<Publication>();
	public HashMap<Long, NodeVisual> nodeVisualMap = new HashMap<Long, NodeVisual>();
	public HashMap<Long, NodeVisual> oldNodeVisualMap;
	public LinkedList<NodeVisual> nodeVisuals = new LinkedList<NodeVisual>();
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public GraphCell(int row, int col) {
		gridy = row;
		gridx = col;
	}
	
	public void createAndLayoutNodeVisuals() {
		// save old node visual map
		oldNodeVisualMap = (HashMap<Long, NodeVisual>) nodeVisualMap.clone();
		nodeVisuals.clear();
		nodeVisualMap.clear();
		
		boolean xcollapsed = rootFrame.facetBrowserX.getNodesFilter(gridx).isCollapsed || xAggr;
		boolean ycollapsed = rootFrame.facetBrowserY.getNodesFilter(gridy).isCollapsed || yAggr;		
		Facet xFacet = rootFrame.facetBrowserX.getNodesFilter(gridx).getLayoutFacet();
		Facet yFacet = rootFrame.facetBrowserY.getNodesFilter(gridy).getLayoutFacet();
		
		if (!xcollapsed && !ycollapsed) {
			// one visual for each pub
			createOneVisualForEach(xFacet, yFacet);
		}
		else if (xcollapsed && ycollapsed) {
			// one visual for all pubs
			createOneVisualForAll();
		}
		else if (xcollapsed) {		
			if (yFacet != null) {
				// one visual for all pubs with the same y attribute
				if (yFacet.facetType == Facet.FacetType.NUMERICAL)
					createGroupedVisualNumerical(xFacet, yFacet, true);
				else
					createGroupedVisualCategorical(xFacet, yFacet, true);
			}
			else {
				// one visual for all pubs
				createOneVisualForAll();
			}			
		}
		else if (ycollapsed) {		
			if (xFacet != null) {
				// one visual for all pubs with the same x attribute				
				if (xFacet.facetType == Facet.FacetType.NUMERICAL)
					createGroupedVisualNumerical(xFacet, yFacet, false);
				else
					createGroupedVisualCategorical(xFacet, yFacet, false);
			}
			else {
				// one visual for all pubs
				createOneVisualForAll();
			}
		}
		
		layoutNodeVisuals(xFacet, yFacet, xcollapsed, ycollapsed);
	}

	public void reLayoutNodeVisuals() {
		boolean xcollapsed = rootFrame.facetBrowserX.getNodesFilter(gridx).isCollapsed || xAggr;
		boolean ycollapsed = rootFrame.facetBrowserY.getNodesFilter(gridy).isCollapsed || yAggr;		
		Facet xFacet = rootFrame.facetBrowserX.getNodesFilter(gridx).getLayoutFacet();
		Facet yFacet = rootFrame.facetBrowserY.getNodesFilter(gridy).getLayoutFacet();
		
		layoutNodeVisuals(xFacet, yFacet, xcollapsed, ycollapsed);
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods
	private void layoutNodeVisuals(Facet xFacet, Facet yFacet, boolean xcollapsed, boolean ycollapsed) {		
		if (nodeVisuals.size() == 1) {
			nodeVisuals.get(0).xpos = 0.5;
			nodeVisuals.get(0).ypos = 0.5;
			return;
		}
		
		DataSource.Network network = PivotSlice.dataSource.getNetwork();	
		
		if (matrixLayout) {
			/*
			for (NodeVisual node : nodeVisuals) {
				node.xpos = 0.5;
			}
			forceDirectedLayout(network, false, true);
			
			Collections.sort(nodeVisuals, new Comparator<NodeVisual>(){
				@Override
				public int compare(NodeVisual arg0, NodeVisual arg1) {
					if (arg0.ypos < arg1.xpos)
						return 1;
					else if (arg0.ypos > arg1.ypos)
						return -1;
					else
						return 0;
				}});
			*/
			// matrix-style view
			double delta = 1.0 / (nodeVisuals.size() + 1);
			double pos = delta;
			for (NodeVisual node : nodeVisuals) {
				node.xpos = pos;
				node.ypos = pos;
				pos += delta;
			}
			
			isScattered = false;
		} 
		else {	
			// layout by attributes			
			if (xFacet == null && yFacet == null) {
				// force directed layout
				forceDirectedLayout(network, true, true);
			}
			else if (xFacet != null && yFacet != null) {
				// scatter plot: no need to layout
			}
			else if (xFacet != null && !ycollapsed) {
				// vertical bar chart
				forceDirectedLayout(network, false, true);

			}
			else if (yFacet != null && !xcollapsed) {
				// horizontal bar chart
				forceDirectedLayout(network, true, false);
			}
		}	
		
	}

	private NodeVisual findPreviousNode(Long id) {
		NodeVisual node = oldNodeVisualMap.get(id);
		if (node != null)
			return node;
		
		GraphCanvas parentCanvas = rootFrame.graphCanvas;
		for (int i = 0; i < parentCanvas.getRowLength(); i++)
			for (int j = 0; j < parentCanvas.getColLength(); j++) {
				GraphCell cell = parentCanvas.getGraphCell(i, j);
				if (cell != this) {
					if (cell.oldNodeVisualMap != null)
						node = cell.oldNodeVisualMap.get(id);
					else
						node = cell.nodeVisualMap.get(id);
					
					if (node != null)
						return node;
				}
			}
		
		return null;
	}
	
	private void createOneVisualForEach(Facet xFacet, Facet yFacet) {
		Facet prexFacet = rootFrame.facetBrowserX.getNodesFilter(gridx).getPreLayoutFacet();
		Facet preyFacet = rootFrame.facetBrowserY.getNodesFilter(gridy).getPreLayoutFacet();
		
		for (Publication pub : publications) {
			NodeVisual node = new NodeVisual();		
			node.pubs.add(pub);
			
			if (xFacet != null) {	
				Constraint.ConstraintData xcdata = rootFrame.facetBrowserX.getNodesFilter(gridx)
						.getConstraint(xFacet.facetID).getConstraintData();
				if (xFacet.facetType == Facet.FacetType.NUMERICAL) {
					node.xpos = (Facet.getNumericalFacetValue(pub, xFacet) - xcdata.fromValue + 1) 
							/ (double)(xcdata.toValue - xcdata.fromValue + 2);
				}
				else { 					
					LinkedList<Long> ids = Facet.getCategoricalFacetValueIDs(pub, xFacet);
					for (int i = 0; i < xcdata.valueIDs.size(); i++) {
						if (ids.contains(xcdata.valueIDs.get(i))) {
							node.xpos = 1.0 / (xcdata.valueIDs.size() + 1) * (i + 1);
							break;
						}
					}
				}
			}
			
			if (yFacet != null) {	
				Constraint.ConstraintData ycdata = rootFrame.facetBrowserY.getNodesFilter(gridy)
						.getConstraint(yFacet.facetID).getConstraintData();
				if (yFacet.facetType == Facet.FacetType.NUMERICAL) {
					node.ypos = 1.0 - (Facet.getNumericalFacetValue(pub, yFacet) - ycdata.fromValue + 1) 
							/ (double)(ycdata.toValue - ycdata.fromValue + 2);
				}
				else { 						
					LinkedList<Long> ids = Facet.getCategoricalFacetValueIDs(pub, yFacet);
					for (int i = 0; i < ycdata.valueIDs.size(); i++) {
						if (ids.contains(ycdata.valueIDs.get(i))) {
							node.ypos = 1.0 - 1.0 / (ycdata.valueIDs.size() + 1) * (i + 1);
							break;
						}
					}
				}
			}
			
			NodeVisual pnode = findPreviousNode(pub.getID());
			if (pnode != null) {
				node.status = pnode.status;
				node.xrenderp = pnode.xrender;
				node.yrenderp = pnode.yrender;
				
				if(isScattered && oldNodeVisualMap.containsKey(pub.getID()) 
						&& xFacet == prexFacet && yFacet == preyFacet) {
					node.xpos = pnode.xpos;
					node.ypos = pnode.ypos;
					node.isFixed = true;
				}
			}
			
			nodeVisuals.add(node);
			nodeVisualMap.put(pub.getID(), node);
		}
		
		isScattered = true;
	}
	
	private void createOneVisualForAll() {
		NodeVisual node = new NodeVisual();
		node.pubs.addAll(publications);
		node.xpos = 0.5;
		node.ypos = 0.5;
		nodeVisuals.add(node);
		
		for (Publication pub : publications)
			nodeVisualMap.put(pub.getID(), node);
		
		isScattered = false;
	}
	
	private void createGroupedVisualCategorical(Facet xFacet, Facet yFacet, boolean xAggregation) {
		// group by the first value of the attribute, e.g., the first author
		if (xAggregation) {
			Constraint.ConstraintData cdata = rootFrame.facetBrowserY.getNodesFilter(gridy)
					.getConstraint(yFacet.facetID).getConstraintData();
			NodeVisual[] nodes = new NodeVisual[cdata.valueIDs.size()];
			for (int i = 0; i < nodes.length; i++) {
				nodes[i] = new NodeVisual();
				nodes[i].xpos = 0.5;
				nodes[i].ypos = 1.0 - 1.0 / (nodes.length + 1) * (i + 1);
				nodeVisuals.add(nodes[i]);
			}
			
			for (Publication pub : publications) {
				LinkedList<Long> ids = Facet.getCategoricalFacetValueIDs(pub, yFacet);
				for (int i = 0; i < cdata.valueIDs.size(); i++) {
					if (ids.contains(cdata.valueIDs.get(i))) {
						nodes[i].pubs.add(pub);
						nodeVisualMap.put(pub.getID(), nodes[i]);
						break;
					}
				}

			}
		}
		else {
			Constraint.ConstraintData cdata = rootFrame.facetBrowserX.getNodesFilter(gridx)
					.getConstraint(xFacet.facetID).getConstraintData();
			NodeVisual[] nodes = new NodeVisual[cdata.valueIDs.size()];
			for (int i = 0; i < nodes.length; i++) {
				nodes[i] = new NodeVisual();
				nodes[i].xpos = 1.0 / (nodes.length + 1) * (i + 1);
				nodes[i].ypos = 0.5;
				nodeVisuals.add(nodes[i]);
			}
			
			for (Publication pub : publications) {
				LinkedList<Long> ids = Facet.getCategoricalFacetValueIDs(pub, xFacet);
				for (int i = 0; i < cdata.valueIDs.size(); i++) {
					if (ids.contains(cdata.valueIDs.get(i))) {
						nodes[i].pubs.add(pub);
						nodeVisualMap.put(pub.getID(), nodes[i]);
						break;
					}
				}
			}
		}
		
		isScattered = false;
	}
	
	private void createGroupedVisualNumerical(Facet xFacet, Facet yFacet, boolean xAggregation) {
		if (xAggregation) {
			Constraint.ConstraintData cdata = rootFrame.facetBrowserY.getNodesFilter(gridy)
					.getConstraint(yFacet.facetID).getConstraintData();
			NodeVisual[] nodes = new NodeVisual[cdata.toValue - cdata.fromValue + 1];
			for (int i = 0; i < nodes.length; i++) {
				nodes[i] = new NodeVisual();
				nodes[i].xpos = 0.5;
				nodes[i].ypos = 1.0 - 1.0 / (nodes.length + 1) * (i + 1);
				nodeVisuals.add(nodes[i]);
			}
			
			for (Publication pub : publications) {
				int idx = Facet.getNumericalFacetValue(pub, yFacet) -  cdata.fromValue;
				nodes[idx].pubs.add(pub);
				nodeVisualMap.put(pub.getID(), nodes[idx]);
			}
		}
		else {
			Constraint.ConstraintData cdata = rootFrame.facetBrowserX.getNodesFilter(gridx)
					.getConstraint(xFacet.facetID).getConstraintData();
			NodeVisual[] nodes = new NodeVisual[cdata.toValue - cdata.fromValue + 1];
			for (int i = 0; i < nodes.length; i++) {
				nodes[i] = new NodeVisual();
				nodes[i].xpos = 1.0 / (nodes.length + 1) * (i + 1);
				nodes[i].ypos = 0.5;
				nodeVisuals.add(nodes[i]);
			}
			
			for (Publication pub : publications) {
				int idx = Facet.getNumericalFacetValue(pub, xFacet) -  cdata.fromValue;
				nodes[idx].pubs.add(pub);
				nodeVisualMap.put(pub.getID(), nodes[idx]);
			}
		}
		
		isScattered = false;
	}

	private void forceDirectedLayout(DataSource.Network network, boolean xfree, boolean yfree) {
		Random rand = new Random(46);
		for (NodeVisual node : nodeVisuals) {
			if (node.isFixed)
				continue;
			
			if (xfree)
				node.xpos = rand.nextDouble();
			if (yfree)
				node.ypos = rand.nextDouble();
		}
		
		if (nodeVisuals.size() == 0)
			return;
		
		double k = Math.sqrt(1.0 / nodeVisuals.size());
		double temperature = startTemp;
		
		for (int i = 0; i < layoutIteration; i++) {
			for (NodeVisual node : nodeVisuals) {
				node.xdisp = 0;
				node.ydisp = 0;
			}
			
			for (NodeVisual node1 : nodeVisuals) {
				// repulsive force
				for (NodeVisual node2 : nodeVisuals) {
					if (node1 == node2) 
						continue;
					double dx = node1.xpos - node2.xpos;
					double dy = node1.ypos - node2.ypos;
					double dist = Math.max(EPSILON, Math.sqrt(dx * dx + dy * dy));
					double f = k * k / dist / dist ;
					if (xfree)
						node1.xdisp += f * dx;
					if (yfree)
						node1.ydisp += f * dy;
				}
				
				// attractive force
				for (Publication pub : node1.pubs) {
					// in edges
					for (Long toID : network.graphEdgesIn.get(pub.getID())) {
						NodeVisual node2 = nodeVisualMap.get(toID);
						if (node2 != null) {
							double dx = (node1.xpos - node2.xpos);
							double dy = (node1.ypos - node2.ypos);
							double dist = Math.sqrt(dx * dx + dy * dy);
							double f = dist * dist / k;
							if (xfree) {
								node1.xdisp -= f * dx;
								node2.xdisp += f * dx;
							}
							if (yfree) {
								node1.ydisp -= f * dy;
								node2.ydisp += f * dy;
							}
						}
					}
					// out edges
					for (Long toID : network.graphEdgesOut.get(pub.getID())) {
						NodeVisual node2 = nodeVisualMap.get(toID);
						if (node2 != null) {
							double dx = node1.xpos - node2.xpos;
							double dy = node1.ypos - node2.ypos;
							double dist = Math.max(EPSILON, Math.sqrt(dx * dx + dy * dy));
							double f = dist / k;
							if (xfree) {
								node1.xdisp -= f * dx;
								node2.xdisp += f * dx;
							}
							if (yfree) {								
								node1.ydisp -= f * dy;								
								node2.ydisp += f * dy;
							}

						}
					}
				}	
			}
			
			// apply force
			for (NodeVisual node : nodeVisuals) {
				if (node.isFixed)
					continue;
				
				double disp = Math.max(EPSILON, Math.sqrt(node.xdisp * node.xdisp + node.ydisp * node.ydisp));
				if (xfree) {
					node.xpos += node.xdisp / disp * Math.min(temperature, dispRatio * disp);
					node.xpos = node.xpos < 0 ? 0.2 * Math.random() : node.xpos;
					node.xpos = node.xpos > 1 ? 1 - 0.2 * Math.random() : node.xpos;
				}
				if (yfree) {
					node.ypos += node.ydisp / disp * Math.min(temperature, dispRatio * disp);	
					node.ypos = node.ypos < 0 ? 0.2 * Math.random() : node.ypos;				
					node.ypos = node.ypos > 1 ? 1 - 0.2 * Math.random() : node.ypos;
				}
			}
			
			// cool
			temperature *= (1.0 - i / (double) layoutIteration);
		}
	}
	
	public static class NodeVisual implements Serializable {
		private static final long serialVersionUID = 1639343932650767865L;
		
		public VisualStatus status = VisualStatus.DEFAULT;
		public double xpos, ypos;
		public double xrender, yrender;
		public double xrenderp, yrenderp;
		public double xdisp, ydisp;
		public double xlayout, ylayout;
		public boolean isNew;
		public boolean isFixed;
		public Ellipse2D.Double circle;
		public ArrayList<Publication> pubs = new ArrayList<Publication>();	
	}
	
	public static class EdgeVisual implements Serializable {
		private static final long serialVersionUID = 286473153530261535L;
		
		public VisualStatus status = VisualStatus.DEFAULT;
		public Edge edgeNodes = new Edge();
		public int count;
		public boolean isCrossing;
		public QuadCurve2D.Double line;
		public Rectangle2D.Double rect;
		
		public EdgeVisual(NodeVisual node1, NodeVisual node2, QuadCurve2D.Double curve) {
			edgeNodes.fromNode = node1;
			edgeNodes.toNode = node2;
			line = curve;
			count = 1;
		}
		
		public EdgeVisual(NodeVisual node1, NodeVisual node2, Rectangle2D.Double square) {
			edgeNodes.fromNode = node1;
			edgeNodes.toNode = node2;
			rect = square;
			count = 1;
		}
		
		public static class Edge implements Serializable {
			private static final long serialVersionUID = 3058092947843865445L;
			
			public NodeVisual fromNode;
			public NodeVisual toNode;
			
			@Override
			public boolean equals(Object other) {
				if (other == this)
					return true;
				
				if (other == null || other.getClass() != this.getClass())
					return false;
				
				Edge oe = (Edge) other;
				return fromNode == oe.fromNode && toNode == oe.toNode;

			}
			
			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime * result + String.format("%f", fromNode.xrender).hashCode();
				result = prime * result + String.format("%f", fromNode.yrender).hashCode();
				result = prime * result + fromNode.pubs.hashCode();
				result = prime * result + toNode.pubs.hashCode();
				
				return result;
			}
		}
	}
}
