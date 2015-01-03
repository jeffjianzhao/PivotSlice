package pivotslice;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.swing.JFileChooser;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import pivotslice.GraphCell.EdgeVisual;
import pivotslice.GraphCell.NodeVisual;


import com.microsoft.research.Publication;

public class GraphCanvas extends JPanel implements ComponentListener, MouseListener, MouseMotionListener {
	
	public static enum VisualStatus {DEFAULT, BRUSHED, HOVERED, SELECTED};
	public static enum GraphCellOp {ADD, REMOVE, UPDATE, RELAYOUT};
	
	private static final int stepNum = 20;
	private static final double nodeSize = 3;
	private static final double edgeBending = 12;
	private static final double cellMargin = 5;;
	private static final double edgeSize = 2;
	private static final Color edgeInitColor = new Color(150, 150, 150, 25);
	private static final Color edgeInitColorMat = new Color(100, 100, 100, 80);
	private static final Color edgeInitColorEm = new Color(65, 105, 225, 80);
	private static final Color edgeEmphColorOut = new Color(77, 175, 74, 100);
	private static final Color edgeEmphColorIn = new Color(255, 127, 0, 100);
	private static final Color nodeInitColor = new Color(25, 25, 112, 100);
	private static final Color nodeEmphColor = new Color(165, 42, 42, 100);
	private static final Color nodeSelColor = new Color(220, 20, 60, 200);
	private static final Color nodeNewColor = new Color(150, 30, 30, 100);
	private static final Color selectionAreaColor = new Color(255, 0, 0, 100);
	private static final Color bkgrTextColor = new Color(0, 0, 0, 120);
	private static final Color cellSelColor = new Color(220, 20, 60);
	private static final BasicStroke defaultStroke = new BasicStroke(1);
	
	public boolean initialized = false;
	
	private Timer timer;
	private int currentStep = stepNum;
	private Polygon arrowHead = new Polygon(); 
	private boolean internalLink = false;
	private boolean crossingLink = false;
	
	private NodeVisual hoveredNode, preHoveredNode;
	private HashSet<NodeVisual> selectedNodes = new HashSet<NodeVisual>();
	private HashSet<NodeVisual> selectedNgbrNodes = new HashSet<NodeVisual>();
	private HashSet<Publication> selectedPublications = new HashSet<Publication>();
	private GraphCell hoveredCell, selectedCell;
	private boolean lassoSelection;
	private LinkedList<Point> selectionPoints = new LinkedList<Point>();
	private Point currentPoint;
	private Polygon selectionArea = new Polygon();
	
	private ArrayList<GraphCellRow> graphCellRows = new ArrayList<GraphCellRow>();
	
	private boolean highlightRepaint = false;
	private BufferedImage graphEdgesImage;
	private BufferedImage highlightEdgesImage;
	private JLayeredPane layers = new JLayeredPane();
	private BackgroundPanel backgroundLayer = new BackgroundPanel();
	private GraphPanel graphLayer = new GraphPanel();
	private HighlightPanel highlightLayer = new HighlightPanel();
	private SelectionPanel selectionLayer = new SelectionPanel();
	
	private JPopupMenu popupMenu = new JPopupMenu();
	private String[] menuItems = new String[]{"Select citations", "Select references", "Select all", "-", 
			"Bring citations", "Bring references", "Prune data", "Load data", "Save data", "-", 
			"Toggle matrix view", "Toggle aggregation by x-axis", "Toggle aggregation by y-axis"};
	
	private final PivotSlice rootFrame;
	
	public GraphCanvas(PivotSlice frame) {
		rootFrame = frame;
		GraphCell.rootFrame =  frame;
		
		this.setLayout(new BorderLayout());
		this.setFocusable(true);
		this.setBackground(Color.white);
		this.setDoubleBuffered(true);
		
		this.addComponentListener(this);
		this.addMouseListener(this);
		this.addMouseMotionListener(this);
		
		this.add(layers, BorderLayout.CENTER);
		
		layers.add(graphLayer, new Integer(100));
		layers.add(backgroundLayer, new Integer(101));
		layers.add(highlightLayer, new Integer(102));
		layers.add(selectionLayer, new Integer(103));
		
		backgroundLayer.setOpaque(false);
		graphLayer.setOpaque(false);
		highlightLayer.setOpaque(false);
		selectionLayer.setOpaque(false);
		
		arrowHead.addPoint(0, 0);
		arrowHead.addPoint(-3, -10);
		arrowHead.addPoint(3, -10);
		
		timer = new Timer(100, new RepaintActionListener());
		
		MenuActionListener listener = new MenuActionListener();
		for (String name : menuItems) {
			if (name.equals("-"))
				popupMenu.add(new JSeparator());
			else {
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(listener);
				popupMenu.add(item);
			}
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public GraphCell getGraphCell(int row, int col) {
		return graphCellRows.get(row).graphCells.get(col);
	}
	
	public void initGraphCanvas() {
		if (graphCellRows.size() != 0)
			graphCellRows.clear();
		graphCellRows.add(new GraphCellRow());
		graphCellRows.get(0).graphCells.add(new GraphCell(0, 0));
		
		DataSource.Network network = PivotSlice.dataSource.getNetwork();
		GraphCell defaultCell = getGraphCell(0, 0);
		defaultCell.publications.addAll(network.graphNodes.values());	
		defaultCell.createAndLayoutNodeVisuals();
		
		Dimension dim = this.getSize();		
		graphEdgesImage = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB); 
		highlightEdgesImage = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB); 
		backgroundLayer.setBounds(0, 0, dim.width, dim.height);
		graphLayer.setBounds(0, 0, dim.width, dim.height);
		highlightLayer.setBounds(0, 0, dim.width, dim.height);
		selectionLayer.setBounds(0, 0, dim.width, dim.height);
		
		selectedCell = defaultCell;
		selectedPublications.clear();
		
		createDrawings();
		highlightSelectedNodes();
			
		initialized = true;
		
		rootFrame.gridPanel.updateDrawings();
		repaintImmediately();
		//repaintAnimation();
	}
	
	public void writeObjects(ObjectOutputStream obs) throws IOException {
		obs.writeObject(graphCellRows);
		obs.writeObject(selectedCell);
		obs.writeObject(selectedNodes);
		obs.writeObject(selectedNgbrNodes);
	}
	
	public void readObjects(ObjectInputStream obs) throws IOException, ClassNotFoundException {
		graphCellRows = (ArrayList<GraphCellRow>) obs.readObject();
		selectedCell = (GraphCell) obs.readObject();
		selectedNodes = (HashSet<NodeVisual>) obs.readObject();
		selectedNgbrNodes = (HashSet<NodeVisual>) obs.readObject();
		
		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() {
				createDrawings();
				rootFrame.gridPanel.updateDrawings();
				repaintImmediately();
			}	
		});
	}
	
	public void reDistributeNodesToAllGraphCells() {
		// called when the data model is changed
		for (int i = 0; i < getRowLength(); i++) {
			for (int j = 0; j < getColLength(); j++) {
				getGraphCell(i, j).publications.clear();
			}
		}
		
		DataSource.Network network = PivotSlice.dataSource.getNetwork();
		for (Publication pub : network.graphNodes.values()) {
			boolean found = false;
			// double constraint cells
			for (int i = 1; i < getRowLength(); i++) {
				for (int j = 1; j < getColLength(); j++) {
					if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, j) && 
								rootFrame.facetBrowserY.satisfyFilterConstraints(pub, i)) {
						getGraphCell(i, j).publications.add(pub);
						found = true;
					}
				}
			}
			// single constraint cells
			if (!found) {
				found = false;
				for (int i = 1; i < getRowLength(); i++) {
					if (rootFrame.facetBrowserY.satisfyFilterConstraints(pub, i)) {
						getGraphCell(i, 0).publications.add(pub);
						found = true;
					}
				}
				
				for (int j = 1; j < getColLength(); j++) {
					if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, j)) {
						getGraphCell(0, j).publications.add(pub);
						found = true;
					}
				}
				// no constraint cell - the default cell	
				if (!found)
					getGraphCell(0, 0).publications.add(pub);
			}
				
		}
		
		for (int i = 0; i < getRowLength(); i++) {
			for (int j = 0; j < getColLength(); j++) {
				getGraphCell(i, j).createAndLayoutNodeVisuals();
			}
		}
		
		createDrawings();
		repaintAnimation();
	}
	
	public void performDataChangeToGraphCells(boolean isRow, int index, GraphCellOp op) {
		switch(op) {
		case ADD:
			if (isRow) {
				// create new graph cells
				GraphCellRow newRow = new GraphCellRow();
				for (int i = 0; i < getColLength(); i++)
					newRow.graphCells.add(new GraphCell(index, i));
				graphCellRows.add(index, newRow);
				// update index
				for (int i = index + 1; i < getRowLength(); i++)
					for (GraphCell cell : graphCellRows.get(i).graphCells) {
						cell.gridy = i;
					}
			}
			else {
				// create new graph cells
				for (int i = 0; i < getRowLength(); i++)
					graphCellRows.get(i).graphCells.add(index, new GraphCell(i, index));
				// update index
				for (int i = index + 1; i < getColLength(); i++)
					for (int j = 0; j < getRowLength(); j++){
						getGraphCell(j, i).gridx = i;
					}
			}
			
			redistributePublications(isRow, index);
			
			break;
			
		case REMOVE:
			if (isRow) {
				GraphCellRow row = graphCellRows.remove(index);
				// update index
				for (int i = index; i < graphCellRows.size(); i++) {
					for (GraphCell cell : graphCellRows.get(i).graphCells) {
						cell.gridy = i;
					}
				}
				
				for (int i = 0; i < getColLength(); i++) {
					GraphCell defaultCell = getGraphCell(0, i);
					// remove duplicates
					for (Publication pub : row.graphCells.get(i).publications) {
						boolean duplicated = false;
						for (int j = 1; j < graphCellRows.size(); j++) {
							if (getGraphCell(j, i).nodeVisualMap.containsKey(pub.getID())) {
								duplicated = true;
								break;
							}
						}
						if (!duplicated)
							defaultCell.publications.add(pub);
					}
					
					defaultCell.createAndLayoutNodeVisuals();
				}
				
				if (selectedCell != null && index == selectedCell.gridy) {
					selectedCell = null;
				}
			}
			else {
				for (int i = 0; i < getRowLength(); i++) {
					GraphCell defaultCell = getGraphCell(i, 0);
					GraphCell cell = graphCellRows.get(i).graphCells.remove(index);
					// update index
					for (int j = index; j < graphCellRows.get(i).graphCells.size(); j++) {
						graphCellRows.get(i).graphCells.get(j).gridx = j;
					}
					
					// remove duplicates
					for (Publication pub : cell.publications) {
						boolean duplicated = false;
						for (int j = 1; j < graphCellRows.get(i).graphCells.size(); j++) {
							if (getGraphCell(i, j).nodeVisualMap.containsKey(pub.getID())) {
								duplicated = true;
								break;
							}
						}
						if (!duplicated)
							defaultCell.publications.add(pub);
					}
					
					defaultCell.createAndLayoutNodeVisuals();	
					
					if (selectedCell != null && index == selectedCell.gridx) {
						selectedCell = null;
					}
				}
			}
			
			break;
			
		case UPDATE:
			redistributePublications(isRow, index);
			
			break;
			
		case RELAYOUT:
			if (isRow) {
				for(GraphCell cell : graphCellRows.get(index).graphCells) {
					cell.createAndLayoutNodeVisuals();
				}
			}
			else {
				for(int i = 0; i < graphCellRows.size(); i++) {
					getGraphCell(i, index).createAndLayoutNodeVisuals();		
				}
			}
			
			break;
		}
	}
	
	public void performAnimatedRendering() {
		createDrawings();
		repaintAnimation();
	}
	
	public int getRowLength() {
		return graphCellRows.size();
	}
	
	public int getColLength() {
		return graphCellRows.get(0).graphCells.size();
	}
	
	public GraphCell getSelectedCell() {
		return selectedCell;
	}
	
	public GraphCell getHoveredCell() {
		return hoveredCell;
	}
	
	public Set<NodeVisual> getSelectedNodes() {
		return selectedNodes;
	}
	
	public Set<Publication> getSelectedPublications() {
		return selectedPublications;
	}
	
	public GraphCell getGraphCellContainsNode(NodeVisual node) {
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				if (rowCells.get(j).bounds.contains(node.xrender, node.yrender))
					return rowCells.get(j);
			}
		}
		
		return null;
	}
	
	public void setPublicationSelected(Long pubid) {
		selectedPublications.clear();
		selectedPublications.add(PivotSlice.dataSource.getNetwork().graphNodes.get(pubid));
		
		highlightSelectedNodes();
	}
	
	public void setCollapsedView(boolean isCollapsed, boolean isXaxis) {
		if (selectedCell == null)
			return;
		
		if (isXaxis) {
			if (rootFrame.facetBrowserX.getNodesFilters().get(selectedCell.gridx).isCollapsed 
					|| isCollapsed == selectedCell.xAggr)
				return;
			selectedCell.xAggr = isCollapsed;
		}
		else {
			if (rootFrame.facetBrowserY.getNodesFilters().get(selectedCell.gridy).isCollapsed
					|| isCollapsed == selectedCell.yAggr)
				return;
			selectedCell.yAggr = isCollapsed;
		}
		
		selectedCell.createAndLayoutNodeVisuals();
		createDrawings();
		repaintAnimation();
	}
	
	public void setMatrixView(boolean selected) {
		if (selectedCell != null && selectedCell.matrixLayout != selected) {
			selectedCell.matrixLayout = selected;
			selectedCell.createAndLayoutNodeVisuals();
			createDrawings();
			repaintAnimation();
		}
	}
	
	public void reLayoutNodeVisuals() {
		if (selectedCell != null)
			selectedCell.reLayoutNodeVisuals();
		createDrawings();
		repaintAnimation();
	}
	
	public void setShowLinks(boolean internal, boolean crossing) {
		internalLink = internal;
		crossingLink = crossing;
		createDrawings();
		repaintImmediately();
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// events handlers
	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent arg0) {
		if (!initialized)
			return;

		Dimension dim = this.getSize();
		graphEdgesImage = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
		highlightEdgesImage = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
		backgroundLayer.setBounds(0, 0, dim.width, dim.height);
		graphLayer.setBounds(0, 0, dim.width, dim.height);
		highlightLayer.setBounds(0, 0, dim.width, dim.height);
		selectionLayer.setBounds(0, 0, dim.width, dim.height);
		
		createDrawings();
		repaintImmediately();
	}

	@Override
	public void componentShown(ComponentEvent arg0) {}
	
	@Override
	public void mouseDragged(MouseEvent arg0) {}

	@Override
	public void mouseMoved(MouseEvent me) {
		if (me.getButton() != MouseEvent.NOBUTTON || !initialized)
			return;
		
		Point p = me.getPoint();
		
		if (lassoSelection) {
			currentPoint = p;
			selectionLayer.repaint();
			return;
		}
		
		// clear the previous status
		preHoveredNode = hoveredNode;
		if (hoveredNode != null) {
			clearVisualStatus();		
			hoveredNode = null;
		}
		
		// find the node and cell being hovered over
		searchnode:
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				GraphCell cell = rowCells.get(j);
				if (!cell.bounds.contains(p))
					continue;
				if (cell != hoveredCell) {
					hoveredCell = cell;
					highlightRepaint = true;
				}
				// for each node visual
				for (NodeVisual node :cell.nodeVisuals) {
					if (node.circle.contains(p)) {
						node.status = VisualStatus.HOVERED;
						hoveredNode = node;
						break searchnode;
					}
				}
			}
		}
		/*
		// find graph cell being hovered over
		searchcell:
		for (GraphCellRow rows : graphCellRows) {
			for (GraphCell cell : rows.graphCells) {
				if (cell.bounds.contains(p)) {
					hoveredCell = cell;
					break searchcell;
				}
			}
		}
		*/
		highlightHoveredNode();
	}

	@Override
	public void mouseClicked(MouseEvent me) {
		if (me.getButton() != MouseEvent.BUTTON1 || me.getClickCount() > 2)
			return;
		
		Point p = me.getPoint();
		if (me.getClickCount() == 2 && me.isShiftDown()) {
			lassoSelection = !lassoSelection;
			if (!lassoSelection) {	
				selectionPoints.add(p);		// end point

				// find selected publications
				selectedCell = null;
				selectedPublications.clear();
				
				if (me.isControlDown() && !selectedNodes.isEmpty()) {
					for(NodeVisual node : selectedNodes)
						if (selectionArea.contains(node.xrender, node.yrender)) {		
							selectedPublications.addAll(node.pubs);
						}	
					for(NodeVisual node : selectedNgbrNodes)
						if (selectionArea.contains(node.xrender, node.yrender)) {		
							selectedPublications.addAll(node.pubs);
						}	
				}
				
				// for each graph cell
				for (int i = 0; i < graphCellRows.size(); i++) {
					ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
					for (int j = 0; j < rowCells.size(); j++) {
						GraphCell cell = rowCells.get(j);
						if (cell.bounds.contains(p)) 
							selectedCell = cell;
						
						if (!me.isControlDown()) {
							// for each node visual
							for (NodeVisual node : cell.nodeVisuals) {
								if (selectionArea.contains(node.xrender, node.yrender)) {		
									selectedPublications.addAll(node.pubs);
								}	
							}
						}
					}
				}
				
				highlightSelectedNodes();
				selectionLayer.repaint();
			}
			else {
				selectionPoints.clear();
				selectionPoints.add(p);		// start point
				selectionLayer.repaint();
			}
		}
		else if (lassoSelection && me.isShiftDown()) {	// single click in lasso mode
			selectionPoints.add(p);	
			selectionLayer.repaint();
		}
		else if (!me.isShiftDown()) {	// default single click
			selectedCell = null;
			selectedPublications.clear();
			
			if (me.isControlDown()) {	// Ctrl modifier
				boolean previous = false;
				// clicking on node already selected?
				for(NodeVisual node : selectedNodes)
					if (node.circle.contains(p)) {
						selectedNodes.remove(node);
						previous = true;
						break;
					}
				// restore the publication selection
				for(NodeVisual node : selectedNodes)
					selectedPublications.addAll(node.pubs);
				
				// find the cell and/or node being selected
				searchNode:
				for (int i = 0; i < graphCellRows.size(); i++) {
					ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
					for (int j = 0; j < rowCells.size(); j++) {
						GraphCell cell = rowCells.get(j);
						if (!cell.bounds.contains(p))
							continue;
						selectedCell = cell;
						if (previous)
							break searchNode;
						// for each node visual
						for (NodeVisual node : cell.nodeVisuals) {
							if (node.circle.contains(p)) {
								selectedPublications.addAll(node.pubs);
								break searchNode;
							}
						}
					}
				}
			}
			else {
				// find the node and cell being selected
				searchNode:
				for (int i = 0; i < graphCellRows.size(); i++) {
					ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
					for (int j = 0; j < rowCells.size(); j++) {
						GraphCell cell = rowCells.get(j);
						if (!cell.bounds.contains(p))
							continue;
						selectedCell = cell;
						// for each node visual
						for (NodeVisual node : cell.nodeVisuals) {
							if (node.circle.contains(p)) {
								selectedPublications.addAll(node.pubs);
								break searchNode;
							}
						}
					}
				}
			}
			
			highlightSelectedNodes();
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {}

	@Override
	public void mouseExited(MouseEvent arg0) {
		hoveredNode = null;
		preHoveredNode = null;
		hoveredCell = null;
		rootFrame.gridPanel.setHoveredGrid(hoveredCell);
		backgroundLayer.repaint();
	}

	@Override
	public void mousePressed(MouseEvent me) {
		if (me.getButton() == MouseEvent.BUTTON3) {
			popupMenu.show(this, me.getPoint().x, me.getPoint().y);
		}
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		this.requestFocus();
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods - rendering
	private void drawBackground(Graphics2D g2, Dimension dim) {
		g2.setStroke(defaultStroke);
		
		// dividing lines
		g2.setColor(Color.lightGray);	
		for (int i = 1; i < getColLength(); i++) {
			g2.draw(new Line2D.Double(getGraphCell(0, i).bounds.x, 0, getGraphCell(0, i).bounds.x, dim.getHeight()));
		}	
		for (int j = 1; j < getRowLength(); j++) {
			g2.draw(new Line2D.Double(0, getGraphCell(j, 0).bounds.y, dim.getWidth(), getGraphCell(j, 0).bounds.y));
		}
		// background text
		g2.setColor(bkgrTextColor);
		for (int i = 0; i < getRowLength(); i++) {
			for (int j = 0; j < getColLength(); j++) {
				GraphCell cell = getGraphCell(i, j);
				g2.drawString(String.format("N%d:%d", cell.publications.size(), cell.dupNode), 
						(int)cell.bounds.x + 2, (int)cell.bounds.y + 14);
				g2.drawString(String.format("E%d:+%d-%d", cell.edgeTotal, cell.edgeCrossOut, cell.edgeCrossIn), 
						(int)cell.bounds.x + 2, (int)cell.bounds.y + 28);
			}
		}
		
		// selected and hovered cells
		if (!timer.isRunning()) {	
			if (selectedCell != null) {
				g2.setColor(cellSelColor);
				g2.draw(selectedCell.bounds);
			}
			
			if (hoveredCell != null && hoveredCell != selectedCell) {
				g2.setColor(Color.darkGray);
				g2.draw(hoveredCell.bounds);
			}
		}
	}
	
	private void drawGraphNodes(Graphics2D g2) {
		g2.setStroke(defaultStroke);
		// render visual nodes in each graph cell
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				for (NodeVisual node : rowCells.get(j).nodeVisuals) {
					if (timer.isRunning() && node.xrenderp == 0 && node.yrenderp == 0) {
						float[] rgba = nodeInitColor.getComponents(null);
						g2.setColor(new Color(rgba[0], rgba[1], rgba[2], rgba[3] * currentStep / stepNum));
					}
					else if (node.isNew) {
						g2.setColor(nodeNewColor);
					}
					else {
						g2.setColor(nodeInitColor);
					}
					
					g2.fill(node.circle);
					
					if (rowCells.get(j).dupNodeVisuals.contains(node)) {
						g2.setColor(Color.black);
						g2.draw(node.circle);
					}	
				}
			}
		}
	}
	
	private void drawGraphNodesTopLayer(Graphics2D g2) {
		g2.setStroke(defaultStroke);
		// render visual nodes in each graph cell
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				for (NodeVisual node : rowCells.get(j).nodeVisuals) {
					if (node.status == VisualStatus.HOVERED || node.status == VisualStatus.SELECTED) {
						g2.setColor(nodeSelColor);
						g2.fill(node.circle);
						//g2.setColor(nodeEmphColor);
						g2.setColor(Color.BLACK);
						g2.draw(node.circle);
					}
					else if (node.status == VisualStatus.BRUSHED) {
						g2.setColor(nodeEmphColor);
						g2.fill(node.circle);
						g2.setColor(Color.BLACK);
						g2.draw(node.circle);
					}
				}
			}
		}
	}
	
	private void drawGraphEdges(Graphics2D g2) {
		for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
			if (edge.line != null) {
				if (crossingLink && edge.isCrossing || internalLink && !edge.isCrossing) {
					g2.setColor(edgeInitColor);
					g2.setStroke(new BasicStroke((float) (Math.log(edge.count) + 1)));
					
					// link
					g2.draw(edge.line);
					
					// arrow
					Graphics2D g = (Graphics2D)g2.create();
					
					AffineTransform transform = g2.getTransform();
				    double angle = Math.atan2(edge.line.y2 - edge.line.ctrly, edge.line.x2 - edge.line.ctrlx);
				    transform.translate(edge.line.x2, edge.line.y2 - nodeSize);
				    transform.rotate(angle - Math.PI / 2, 0, nodeSize);  	 
				    
				    g.setTransform(transform);
				    g.setColor(edgeInitColor);	
				    g.draw(arrowHead);
				    g.fill(arrowHead);
				    
				    g.dispose();
				}
			}
			else {
				g2.setColor(edgeInitColorMat);
				g2.fill(edge.rect);
			}
		}
	}
	
	private void drawGraphEdgesTopLayer(Graphics2D g2) {		
		for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
			if (selectedNodes.contains(edge.edgeNodes.fromNode) || edge.edgeNodes.fromNode == hoveredNode)
				g2.setColor(edgeEmphColorIn);
			else if (selectedNodes.contains(edge.edgeNodes.toNode) || edge.edgeNodes.toNode == hoveredNode)
				g2.setColor(edgeEmphColorOut);
			else
				continue;
			
			if (edge.line != null) {
				if (edge.edgeNodes.fromNode != hoveredNode && edge.edgeNodes.toNode != hoveredNode
						&& hoveredCell != null && hoveredCell != selectedCell) {				
					GraphCell fromCell = getGraphCellContainsNode(edge.edgeNodes.fromNode);
					GraphCell toCell = getGraphCellContainsNode(edge.edgeNodes.toNode);
					if (!(fromCell == hoveredCell && toCell == selectedCell) 
							&& !(toCell == hoveredCell && fromCell == selectedCell))
						g2.setColor(edgeInitColorEm);
						
				}
				
				// link
				g2.setStroke(new BasicStroke((float) (Math.log(edge.count) + 1)));
				g2.draw(edge.line);
				
				// arrow
				Graphics2D g = (Graphics2D)g2.create();
				
				AffineTransform transform = g2.getTransform();
			    double angle = Math.atan2(edge.line.y2 - edge.line.ctrly, edge.line.x2 - edge.line.ctrlx);
			    transform.translate(edge.line.x2, edge.line.y2 - nodeSize);
			    transform.rotate(angle - Math.PI / 2, 0, nodeSize);  	 	  
			    g.setTransform(transform);	    
			    
			    g.draw(arrowHead);
			    g.fill(arrowHead);
			    
			    g.dispose();
			}
			else {
				g2.fill(edge.rect);
				g2.setColor(Color.black);
				g2.draw(edge.rect);
			}
		}
	}
	
	private void createDrawings() {
		Dimension dim = this.getSize();
		// node drawings
		for (GraphCellRow cellrow : graphCellRows) {
			for (GraphCell cell : cellrow.graphCells) {
				NodesFilter xFilter = rootFrame.facetBrowserX.getNodesFilters().get(cell.gridx);
				NodesFilter yFilter = rootFrame.facetBrowserY.getNodesFilters().get(cell.gridy);
				cell.bounds = new Rectangle2D.Double(xFilter.getBounds().getMinX(), yFilter.getBounds().getMinY(), 
						xFilter.getBounds().getWidth(), yFilter.getBounds().getHeight());
				
				for (NodeVisual node : cell.nodeVisuals) {
					if (node.xrender != 0)
						node.xrenderp = node.xrender;
					if (node.yrender != 0)
						node.yrenderp = node.yrender;
					node.xrender = cell.bounds.x + cellMargin + (cell.bounds.width - 2 * cellMargin) * node.xpos;
					node.yrender = cell.bounds.y + cellMargin + (cell.bounds.height - 2 *cellMargin) * node.ypos;	
					double r = nodeSize * (2 * Math.log(node.pubs.size()) + 1);
					if (node.circle == null) {
						node.circle = new Ellipse2D.Double(node.xrenderp - r, node.yrenderp - r, r * 2,  r * 2);
					}
					else {
						node.circle.x = node.xrenderp - r;
						node.circle.y = node.yrenderp - r;
						node.circle.width = node.circle.height =  r * 2;
					}
					
					if (PivotSlice.dataSource.getNetwork().newGraphNodes.size() > 0) {
						for (Publication pub : node.pubs) 
							if (PivotSlice.dataSource.getNetwork().newGraphNodes.containsKey(pub.getID())) {
								node.isNew = true;
								break;
							}
					}
				}
			}
		}
		
		// edge drawings
		GraphCell.edgeVisuals.clear();
		// for each graph cell
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				rowCells.get(j).edgeTotal = 0;
				// for each visual node in the cell
				for (NodeVisual fromNode : rowCells.get(j).nodeVisuals) {
					createEdgesFromNodeVisual(fromNode, rowCells.get(j));
				}
			}
		}
		
		for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
			GraphCell fromCell = getGraphCellContainsNode(edge.edgeNodes.fromNode);
			GraphCell toCell = getGraphCellContainsNode(edge.edgeNodes.toNode);
			if (fromCell == null || toCell == null)
				continue;
			
			fromCell.edgeTotal += edge.count;
			if (toCell != fromCell) {
				toCell.edgeTotal += edge.count;
				edge.isCrossing = true;
			}
		}
		
		repaintEdgesToImage();
	}

	private void createEdgesFromNodeVisual(NodeVisual fromNode, GraphCell fromCell) {
		DataSource.Network network = PivotSlice.dataSource.getNetwork();
		// for each publication in the node
		for (Publication pub : fromNode.pubs) {
			for (Long toID : network.graphEdgesOut.get(pub.getID())) {
				// for each graph cell, search for the toNode
				for (int i = 0; i < graphCellRows.size(); i++) {
					ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
					for (int j = 0; j < rowCells.size(); j++) {
						GraphCell cell = rowCells.get(j);
						// see if the toNode exists
						NodeVisual toNode = cell.nodeVisualMap.get(toID);
						if (toNode != null && toNode != fromNode) {
							EdgeVisual.Edge ed = new EdgeVisual.Edge();
							ed.fromNode = fromNode;
							ed.toNode = toNode;
							
							if (GraphCell.edgeVisuals.containsKey(ed)) {
								GraphCell.edgeVisuals.get(ed).count++;
								continue;
							}
							
							if (fromCell.matrixLayout && fromCell == cell) {
								// square-shaped edge
								Rectangle2D.Double rect = new Rectangle2D.Double(fromNode.xrender - edgeSize, 
										toNode.yrender - edgeSize, edgeSize * 2, edgeSize * 2);
								
								EdgeVisual ev = new EdgeVisual(fromNode, toNode, rect);
								GraphCell.edgeVisuals.put(ev.edgeNodes, ev);
							}
							else {	
								// curve-shaped edge
								double angle = Math.atan2(toNode.yrender - fromNode.yrender, 
										toNode.xrender - fromNode.xrender) - Math.PI /2;
								double bend = Math.abs(toNode.yrender - fromNode.yrender) + Math.abs(toNode.xrender - fromNode.xrender);
								bend = Math.log(bend + 1) * edgeBending + 8;
								double ctrlx = fromNode.xrender * 0.9 + toNode.xrender * 0.1 + bend * Math.cos(angle);
								double ctrly = fromNode.yrender * 0.9 + toNode.yrender * 0.1 + bend * Math.sin(angle);
								QuadCurve2D.Double line = new QuadCurve2D.Double(fromNode.xrender, fromNode.yrender, 
										ctrlx, ctrly, toNode.xrender, toNode.yrender);
								
								EdgeVisual ev = new EdgeVisual(fromNode, toNode, line);
								GraphCell.edgeVisuals.put(ev.edgeNodes, ev);
							}
						}
					}
				}
			}
		}
	}
	
	private void clearVisualStatus() {
		// for each graph cell
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				// for each node visual
				for (NodeVisual node : rowCells.get(j).nodeVisuals) {
					if (selectedNodes.contains(node))
						node.status = VisualStatus.SELECTED;
					else if (selectedNgbrNodes.contains(node))
						node.status = VisualStatus.BRUSHED;
					else
						node.status = VisualStatus.DEFAULT;
				}
			}
		}
		
		for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
			if (selectedNodes.size() != 0 && (selectedNodes.contains(edge.edgeNodes.fromNode) 
					|| selectedNodes.contains(edge.edgeNodes.toNode)))
				edge.status = VisualStatus.SELECTED;
			else
				edge.status = VisualStatus.DEFAULT;
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods - tooling
	private void highlightHoveredNode() {
		// set status
		if (hoveredNode != null && !selectedNodes.contains(hoveredNode)) {		
			hoveredNode.status = VisualStatus.HOVERED;
			// for each edge
			for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
				if (edge.edgeNodes.fromNode == hoveredNode &&  !selectedNodes.contains(edge.edgeNodes.toNode)) {
					edge.status = VisualStatus.HOVERED;
					edge.edgeNodes.toNode.status = VisualStatus.BRUSHED;
				}
				else if (edge.edgeNodes.toNode == hoveredNode && !selectedNodes.contains(edge.edgeNodes.fromNode)) {
					edge.status = VisualStatus.HOVERED;
					edge.edgeNodes.fromNode.status = VisualStatus.BRUSHED;
				}
			}
			
			// for each cell, find duplicated node
			for (int i = 0; i < graphCellRows.size(); i++) {
				ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
				for (int j = 0; j < rowCells.size(); j++) {
					if (rowCells.get(j) == hoveredCell)
						continue;
					for (Publication pub : hoveredNode.pubs) {
						NodeVisual dupNode = rowCells.get(j).nodeVisualMap.get(pub.getID());
						if (dupNode != null) {
							dupNode.status = VisualStatus.HOVERED;
						}
					}
				}
			}
			
			rootFrame.logger.logAction("graphcanvas-hover over nodes");
		}
		
		// tooltip
		if (hoveredNode != null) {
			StringBuilder sb = new StringBuilder("<html>");
			for(int i = 0; i < 10 && i < hoveredNode.pubs.size(); i++) {
				String title = hoveredNode.pubs.get(i).getTitle();
				if (title.length() > 30)
					sb.append(title.substring(0, 30) + "...<br/>");
				else
					sb.append(title + "<br/>");
			}
			if (hoveredNode.pubs.size() > 10)
				sb.append("[" + (hoveredNode.pubs.size() - 10) + " more publications]");
			sb.append("</html>");
			this.setToolTipText(sb.toString());
			
			if (hoveredNode != preHoveredNode) {
				highlightRepaint = true;
				rootFrame.infoPanel.displayPublication(new HashSet<Publication>(hoveredNode.pubs), false);
			}
		}
		else {
			this.setToolTipText(null);
			if (preHoveredNode != null) {
				highlightRepaint = true;
				rootFrame.infoPanel.displayPublication(selectedPublications, false);
			}
		}
		
		repaintHighlightEdgesToImage();
		rootFrame.gridPanel.setHoveredGrid(hoveredCell);
		backgroundLayer.repaint();
		highlightLayer.repaint();
	}
	
	private void highlightSelectedNodes() {
		selectedNodes.clear();
		selectedNgbrNodes.clear();
		clearVisualStatus();

		// convert selected publications to nodes
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				for (Publication pub : selectedPublications) {
					NodeVisual node = rowCells.get(j).nodeVisualMap.get(pub.getID());
					if (node != null) {
						node.status = VisualStatus.SELECTED;
						selectedNodes.add(node);
					}
				}
			}
		}
		
		// find neighbors and duplicated nodes
		if (selectedNodes.size() != 0) {
			// for each edge
			for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
				if (selectedNodes.contains(edge.edgeNodes.fromNode)) {
					edge.status = VisualStatus.SELECTED;
					if (!selectedNodes.contains(edge.edgeNodes.toNode)) {
						edge.edgeNodes.toNode.status = VisualStatus.BRUSHED;
						selectedNgbrNodes.add(edge.edgeNodes.toNode);
					}
				}
				else if (selectedNodes.contains(edge.edgeNodes.toNode)) {
					edge.status = VisualStatus.SELECTED;
					edge.edgeNodes.fromNode.status = VisualStatus.BRUSHED;
					selectedNgbrNodes.add(edge.edgeNodes.fromNode);
				}
			}
			
			// for each cell, find duplicated node
			/*
			for (int i = 0; i < graphCellRows.size(); i++) {
				ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
				for (int j = 0; j < rowCells.size(); j++) {
					if (rowCells.get(j) == selectedCell)
						continue;
					for (NodeVisual node : selectedNodes) {
						for (Publication pub : node.pubs) {
							NodeVisual dupNode = rowCells.get(j).nodeVisualMap.get(pub.getID());
							if (dupNode != null) {
								dupNode.status = VisualStatus.SELECTED;
								selectedDuplNodes.add(dupNode);
							}
						}
					}
				}
			}
			*/
			
			rootFrame.logger.logAction("graphcanvas-select nodes");
		}	
		
		// compute graphcell relationships
		for (int i = 0; i < graphCellRows.size(); i++) {
			ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
			for (int j = 0; j < rowCells.size(); j++) {
				GraphCell cell = rowCells.get(j);
				cell.edgeCrossIn = 0;
				cell.edgeCrossOut = 0;
				cell.dupNode = 0;
				cell.dupNodeVisuals.clear();
			}
		}
		
		if (selectedCell != null) {
			// compute edge distributions
			for (EdgeVisual edge : GraphCell.edgeVisuals.values()) {
				GraphCell fromCell = getGraphCellContainsNode(edge.edgeNodes.fromNode);
				GraphCell toCell = getGraphCellContainsNode(edge.edgeNodes.toNode);
				if (fromCell == toCell) {
					if (fromCell == selectedCell) {
						selectedCell.edgeCrossIn += edge.count;
						selectedCell.edgeCrossOut += edge.count;
					}
					continue;
				}
				
				if (fromCell == selectedCell) 
					toCell.edgeCrossIn += edge.count;
				else if (toCell == selectedCell) 
					fromCell.edgeCrossOut += edge.count;
			}
			// compute node duplicates
			for (int i = 0; i < graphCellRows.size(); i++) {
				ArrayList<GraphCell> rowCells = graphCellRows.get(i).graphCells;
				for (int j = 0; j < rowCells.size(); j++) {
					if (rowCells.get(j) == selectedCell)
						continue;
					for (NodeVisual node : rowCells.get(j).nodeVisuals) {
						for (Publication pub : node.pubs) {
							if (selectedCell.nodeVisualMap.containsKey(pub.getID())) {
								rowCells.get(j).dupNode++;
								rowCells.get(j).dupNodeVisuals.add(rowCells.get(j).nodeVisualMap.get(pub.getID()));
								//break;
							}
						}
					}
				}
			}
			
			rootFrame.logger.logAction("graphcanvas-select cell");
		}
		
		// repaint the layers
		highlightRepaint = true;
		repaintHighlightEdgesToImage();
		backgroundLayer.repaint();
		highlightLayer.repaint();
		// update corresponding panels
		rootFrame.infoPanel.displayPublication(selectedPublications, true);
		rootFrame.gridPanel.updateDrawings();
		rootFrame.opPanel.updateButtonStates();
	}
	
	private void redistributePublications(boolean isRow, int index) {
		if (isRow) {
			for (int i = 0; i < getColLength(); i++) {
				// re-allocate publications
				GraphCell defaultCell = getGraphCell(0, i);
				GraphCell newCell = getGraphCell(index, i);
				
				LinkedList<Publication> allPubs = new LinkedList<Publication>();
				allPubs.addAll(defaultCell.publications);
				// remove duplicates
				for (Publication pub : newCell.publications) {
					boolean duplicated = false;
					for (int j = 1; j < getRowLength(); j++) {
						if (j == index)
							continue;
						if (getGraphCell(j, i).nodeVisualMap.containsKey(pub.getID())) {
							duplicated = true;
							break;
						}
					}
					if (!duplicated)
						allPubs.add(pub);
				}
				
				//allPubs.addAll(newCell.publications);
								
				defaultCell.publications.clear();
				newCell.publications.clear();
				// do the allocation
				for (Publication pub : allPubs) {
					if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, i) && 
							rootFrame.facetBrowserY.satisfyFilterConstraints(pub, index)) {
						newCell.publications.add(pub);
					}
					else
						defaultCell.publications.add(pub);
				}				
				// add the duplicates
				for (int j = 1; j < getRowLength(); j++) {
					if (j == index)
						continue;
					for (Publication pub : getGraphCell(j, i).publications) {
						if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, i) && 
								rootFrame.facetBrowserY.satisfyFilterConstraints(pub, index)) {
							newCell.publications.add(pub);
						}
					}
				}
				
				// create visuals and layout
				defaultCell.createAndLayoutNodeVisuals();	
				newCell.createAndLayoutNodeVisuals();
			}
		}
		else {
			for (int i = 0; i < getRowLength(); i++) {
				// re-allocate publications
				GraphCell defaultCell = getGraphCell(i, 0);
				GraphCell newCell = getGraphCell(i, index);
				
				LinkedList<Publication> allPubs = new LinkedList<Publication>();
				allPubs.addAll(defaultCell.publications);				
				// remove duplicates
				for (Publication pub : newCell.publications) {
					boolean duplicated = false;
					for (int j = 1; j < getColLength(); j++) {
						if (j == index)
							continue;
						if (getGraphCell(i, j).nodeVisualMap.containsKey(pub.getID())) {
							duplicated = true;
							break;
						}							
					}
					if (!duplicated)
						allPubs.add(pub);
				}
				//allPubs.addAll(newCell.publications);
				
				defaultCell.publications.clear();
				newCell.publications.clear();
				// do the allocation
				for (Publication pub : allPubs) {
					if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, index) && 
							rootFrame.facetBrowserY.satisfyFilterConstraints(pub, i)) {
						newCell.publications.add(pub);
					}
					else
						defaultCell.publications.add(pub);
				}
				// add the duplicates
				for (int j = 1; j < getColLength(); j++) {
					if (j == index)
						continue;
					for (Publication pub : getGraphCell(i, j).publications) {
						if (rootFrame.facetBrowserX.satisfyFilterConstraints(pub, index) && 
								rootFrame.facetBrowserY.satisfyFilterConstraints(pub, i)) {
							newCell.publications.add(pub);
						}
					}
				}
				
				// create visuals and layout
				defaultCell.createAndLayoutNodeVisuals();	
				newCell.createAndLayoutNodeVisuals();
			}
		}
	}
	
	private void repaintEdgesToImage() {
		Graphics2D g2 = graphEdgesImage.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setBackground(Color.white);
		g2.clearRect(0, 0, graphEdgesImage.getWidth(), graphEdgesImage.getWidth());
		drawGraphEdges(g2);
	}
	
	private void repaintHighlightEdgesToImage() {
		if (!highlightRepaint)
			return;
		highlightRepaint = false;
		
		Graphics2D g2 = highlightEdgesImage.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setBackground(new Color(0, 0, 0, 0));
		g2.clearRect(0, 0, highlightEdgesImage.getWidth(), highlightEdgesImage.getWidth());
		drawGraphEdgesTopLayer(g2);
	}
	
	private void repaintImmediately() {
		// assign current render coordinates
		for (GraphCellRow cellrow : graphCellRows) {
			for (GraphCell cell : cellrow.graphCells) {
				for (NodeVisual node : cell.nodeVisuals) {
						node.circle.x = node.xrender - node.circle.width / 2;
						node.circle.y = node.yrender - node.circle.height / 2;
				}
			}
		}
		
		this.repaint();
	}
	
	private void repaintAnimation() {
		timer.stop();
		currentStep = 0;
		timer.restart();
		//backgroundLayer.repaint();
		rootFrame.opPanel.setProgressValue(0);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	public static class GraphCellRow implements Serializable {
		private static final long serialVersionUID = 5449332061603615448L;
		
		public ArrayList<GraphCell> graphCells = new ArrayList<GraphCell>();
	}

	private class RepaintActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent arg0) {
			currentStep++;
			rootFrame.opPanel.setProgressValue(currentStep * 100 / stepNum);
			
			if (currentStep >= stepNum) {
				timer.stop();
				highlightSelectedNodes();
				backgroundLayer.repaint();
				rootFrame.historyPanel.addState();
			}
			
			for (GraphCellRow cellrow : graphCellRows) {
				for (GraphCell cell : cellrow.graphCells) {
					for (NodeVisual node : cell.nodeVisuals) {
						if (node.xrenderp != 0 && node.yrenderp != 0) {
							node.circle.x = node.xrenderp + (node.xrender - node.xrenderp) * currentStep / stepNum 
									- node.circle.width / 2;
							node.circle.y = node.yrenderp + (node.yrender - node.yrenderp) * currentStep / stepNum 
									- node.circle.height / 2;
						}
						else {
							node.circle.x = node.xrender - node.circle.width / 2;
							node.circle.y = node.yrender - node.circle.height / 2;
						}
					}
				}
			}
			
			graphLayer.repaint();
		}
		
	}
	
	private class BackgroundPanel extends JPanel {
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (!initialized)
				return;
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			drawBackground(g2, this.getSize());
			
			g2.dispose();
		}
	}
	
	private class GraphPanel extends JPanel {
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (!initialized)
				return;
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			if (!timer.isRunning())
				g2.drawImage(graphEdgesImage, null, 0, 0);
			
			drawGraphNodes(g2);
			
			g2.dispose();
		}
	}
	
	private class HighlightPanel extends JPanel {
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (!initialized)
				return;
			
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			if (!timer.isRunning())
				//drawGraphEdgesTopLayer(g2);	
				g2.drawImage(highlightEdgesImage, null, 0, 0);
			drawGraphNodesTopLayer(g2);
			
			g2.dispose();
		}
	}
	
	private class SelectionPanel extends JPanel {
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (!initialized)
				return;
			
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			if (lassoSelection) {
				selectionArea.reset();
				for (Point p : selectionPoints) 
					selectionArea.addPoint(p.x, p.y);
				if (currentPoint != null)
					selectionArea.addPoint(currentPoint.x, currentPoint.y);
				
				g2.setColor(selectionAreaColor);
				g2.fill(selectionArea);
				g2.setColor(Color.black);
				g2.draw(selectionArea);
			}
			
			g2.dispose();
			
		}
	}
	
	private class MenuActionListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			String event = e.getActionCommand();
			if (event.equals("Select citations") && !selectedNodes.isEmpty()) {
				DataSource.Network network = PivotSlice.dataSource.getNetwork();
				selectedPublications.clear();
				for (NodeVisual node : selectedNodes) 
					for (Publication pub : node.pubs) 
						for (Long id : network.graphEdgesIn.get(pub.getID()))
							selectedPublications.add(network.graphNodes.get(id));
				
				highlightSelectedNodes();
				
				rootFrame.logger.logAction("graphcanvas-select citations");
			}
			else if (event.equals("Select references") && !selectedNodes.isEmpty()) {
				DataSource.Network network = PivotSlice.dataSource.getNetwork();
				selectedPublications.clear();
				for (NodeVisual node : selectedNodes) 
					for (Publication pub : node.pubs) 
						for (Long id : network.graphEdgesOut.get(pub.getID()))
							selectedPublications.add(network.graphNodes.get(id));
					
				highlightSelectedNodes();
				
				rootFrame.logger.logAction("graphcanvas-select reference");
			}
			else if (event.equals("Select all") && selectedCell != null) {
				selectedPublications.clear();
				for (NodeVisual node : selectedCell.nodeVisuals) 
					selectedPublications.addAll(node.pubs);
				highlightSelectedNodes();
				
				rootFrame.logger.logAction("graphcanvas-select all");
			}
			else if (event.equals("Bring citations") && !selectedNodes.isEmpty()) {
				int result = rootFrame.showConfirmMessage("This may take a few minutes. Do you want to continue?");
				if (result == JOptionPane.OK_OPTION) 
					rootFrame.opPanel.searchPublications(true);
				
				rootFrame.logger.logAction("graphcanvas-search citations");
			}
			else if (event.equals("Bring references") && !selectedNodes.isEmpty()) {
				int result = rootFrame.showConfirmMessage("This may take a few minutes. Do you want to continue?");
				if (result == JOptionPane.OK_OPTION) 
					rootFrame.opPanel.searchPublications(false);
				
				rootFrame.logger.logAction("graphcanvas-search references");
			}
			else if (event.equals("Prune data") && selectedCell != null) {
				int result = rootFrame.showConfirmMessage("Are you sure to prune the dataset?");
				if (result == JOptionPane.OK_OPTION) 
					rootFrame.opPanel.pruneData();
				
				rootFrame.logger.logAction("graphcanvas-prune data");
			}
			else if(event.equals("Load data")) {
				JFileChooser fc = new JFileChooser();
				int returnVal = fc.showOpenDialog(rootFrame);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					try {
						PivotSlice.dataSource.initNetwork(fc.getSelectedFile());
					} catch (Exception e1) {
						rootFrame.showErrorMessage("Open Error:\n" + e1.toString());
						e1.printStackTrace();
					}
				}
				
				rootFrame.graphCanvas.initGraphCanvas();
				rootFrame.historyPanel.addState();
				
				rootFrame.logger.logAction("graphcanvas-load data");
			}
			else if (event.equals("Save data")) {
				// save dataset to file
				JFileChooser fc = new JFileChooser();
				int returnVal = fc.showSaveDialog(rootFrame);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					try {
						PivotSlice.dataSource.saveNetwork(fc.getSelectedFile());
					} catch (Exception e1) {
						rootFrame.showErrorMessage("Save Error:\n" + e1.toString());
						e1.printStackTrace();
					}
				}
			}
			else if (event.equals("Toggle matrix view") && selectedCell != null) {
				setMatrixView(!selectedCell.matrixLayout);
				rootFrame.opPanel.updateButtonStates();
				
				rootFrame.logger.logAction("graphcanvas-matrix view");
			}
			else if (event.equals("Toggle aggregation by x-axis") && selectedCell != null) {
				setCollapsedView(!selectedCell.xAggr, true);
				rootFrame.opPanel.updateButtonStates();
				
				rootFrame.logger.logAction("graphcanvas-collapse x");
			}
			else if (event.equals("Toggle aggregation by y-axis") && selectedCell != null) {
				setCollapsedView(!selectedCell.yAggr, false);
				rootFrame.opPanel.updateButtonStates();
				
				rootFrame.logger.logAction("graphcanvas-collapse y");
			}
		}
		
	}
}
