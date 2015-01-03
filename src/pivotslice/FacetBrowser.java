package pivotslice;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.microsoft.research.Publication;

public class FacetBrowser extends JPanel implements ComponentListener, MouseListener {
	public static enum Direction {HORIZONTAL, VERTICAL};
	
	private static final int margin = 3;
	
	private ArrayList<Facet> facets = new ArrayList<Facet>();
	private ArrayList<NodesFilter> filters = new ArrayList<NodesFilter>();
	
	public final Direction direction;
	private final PivotSlice rootFrame;
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public FacetBrowser(Direction dir, PivotSlice frame) {
		rootFrame = frame;
		direction = dir;
		
		NodesFilter.rootFrame = frame;
		Constraint.rootFrame = frame;
		ConstraintContent.rootFrame = frame;
		
		this.setFocusable(true);
		this.setBackground(Color.WHITE);
		this.setLayout(null);
		this.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
		
		this.addComponentListener(this);
		this.addMouseListener(this);
		new FacetBrowserDropHandler();
		
		initNodesFilter();
	}
	
	public void initNodesFilter() {
		if (facets.size() != 0)
			facets.clear();
		if (filters.size() != 0) {
			filters.clear();
			this.removeAll();
		}
		// add the default "rest" filter
		NodesFilter nf = new NodesFilter(this);
		filters.add(nf); 		
		this.add(nf);
		
		layoutVisuals();
	}
	
	public Facet lastNumericalFacet(int index) {
		for (int i = facets.size() - 1; i >= 0; i--) {
			Facet f = facets.get(i);
			if (f.facetType == Facet.FacetType.NUMERICAL 
					&& !filters.get(index).getConstraint(f.facetID).getConstraintData().isEmptyConstraint())
				return f; 
		}
		
		return null;		// no numerical facet
	}
	
	public NodesFilter getNodesFilter(int idx) {
		return filters.get(idx);
	}
	
	public ArrayList<NodesFilter> getNodesFilters() {
		return filters;
	}
	
	public ArrayList<Facet> getFacets() {
		return facets;
	}
	
	public void writeObjects(ObjectOutputStream obs) throws IOException {
		obs.writeObject(facets);
		obs.writeObject(filters);
	}
	
	public void readObjects(ObjectInputStream obs) throws IOException, ClassNotFoundException {
		facets.clear();
		for (Facet f : (ArrayList<Facet>) obs.readObject()) {
			facets.add(Facet.availableFacets[f.facetID]);
		}
		
		filters = (ArrayList<NodesFilter>) obs.readObject();
		
		this.removeAll();
		for (NodesFilter f : filters) {
			f.parentBrowser = this;
			this.add(f);
		}
		
		NodesFilter.rootFrame = rootFrame;
		Constraint.rootFrame = rootFrame;
		
		layoutVisuals();
	}
	
	public boolean satisfyFilterConstraints(Publication pub, int index) {
		return filters.get(index).satisfyConstraint(pub);
	}

//	public void clearFilterSelection() {
//		for (NodesFilter f : filters) {
//			f.isSelected = false;
//			f.repaint();
//		}
//	}

	public void layoutVisuals() {
		Dimension dim = this.getSize();
		if (!rootFrame.graphCanvas.initialized) {
			// first time called
			filters.get(0).setBounds(margin, margin, dim.width - 2 * margin, dim.height - 2 * margin);
			return;
		}
		
		int collapsedCount = 0;
		for (NodesFilter f : filters) 
			if (f.isCollapsed)
				collapsedCount++;
		
		if (direction == Direction.HORIZONTAL) {
			int[] nodesums = new int[rootFrame.graphCanvas.getColLength()];
			int allsum = 0;
			for (int i = 0; i < rootFrame.graphCanvas.getColLength(); i++) {
				if (filters.get(i).isCollapsed)
					continue;
				for (int j = 0; j < rootFrame.graphCanvas.getRowLength(); j++) {
					nodesums[i] += rootFrame.graphCanvas.getGraphCell(j, i).publications.size();
					allsum += rootFrame.graphCanvas.getGraphCell(j, i).publications.size();
				}
			}
			
			double widthsum = dim.getWidth() - 2 * margin - NodesFilter.collapsedSize * collapsedCount 
					- NodesFilter.baseSize * (rootFrame.graphCanvas.getColLength() - collapsedCount);
			int height = dim.height - 2 * margin;

			int left = margin;
			for (int i = 0; i < filters.size(); i++) {
				NodesFilter f = filters.get(i);
				if (f.isCollapsed) {
					f.setBounds(left, margin, NodesFilter.collapsedSize, height);
					left += NodesFilter.collapsedSize;
				}
				else {
					int width = (int) (widthsum / allsum * nodesums[i] + NodesFilter.baseSize);
					f.setBounds(left, margin, width, height);
					left += width;
				}
				f.layoutVisuals();
			}
				
		}
		else if (direction == Direction.VERTICAL) {
			int[] nodesums = new int[rootFrame.graphCanvas.getRowLength()];
			int allsum = 0;
			for (int i = 0; i < rootFrame.graphCanvas.getRowLength(); i++) {
				if (filters.get(i).isCollapsed)
					continue;
				for (int j = 0; j < rootFrame.graphCanvas.getColLength(); j++) {
					nodesums[i] += rootFrame.graphCanvas.getGraphCell(i, j).publications.size();
					allsum += rootFrame.graphCanvas.getGraphCell(i, j).publications.size();
				}
			}
			
			double heightsum = dim.getHeight() - 2 * margin - NodesFilter.collapsedSize * collapsedCount
					- NodesFilter.baseSize * (rootFrame.graphCanvas.getRowLength() - collapsedCount);
			int width = dim.width - 2 * margin;

			int top = margin;
			for (int i = 0; i < filters.size(); i++) {
				NodesFilter f = filters.get(i);
				if (f.isCollapsed) {
					f.setBounds(margin, top, width, NodesFilter.collapsedSize);
					top += NodesFilter.collapsedSize;
				}
				else {
					int height = (int) (heightsum / allsum * nodesums[i] + NodesFilter.baseSize);
					f.setBounds(margin, top, width, height);
					top += height;
				}
				f.layoutVisuals();
			}
		}
		
		this.repaint();
	}
	
	public void resizeNodesFilter(NodesFilter f) {
		//if (filters.size() == 1)
		//	return;
		int i = filters.indexOf(f);
		if (i != -1) {
			f.isCollapsed = !f.isCollapsed;
			rootFrame.graphCanvas.performDataChangeToGraphCells(direction == FacetBrowser.Direction.VERTICAL, 
					i, GraphCanvas.GraphCellOp.RELAYOUT);
			layoutVisuals();
			rootFrame.graphCanvas.performAnimatedRendering();
			
			rootFrame.logger.logAction("filter-resize");
		}
	}
	
	public void removeNodesFilter(NodesFilter f) {
		int i = filters.indexOf(f);
		if (i == 0 || i == -1)
			return;
		
		for (Facet fa : facets) {
			Constraint.ConstraintData cons = f.getConstraint(fa.facetID).getConstraintData();
			if (cons.getFacetType() == Facet.FacetType.CATEGORICAL) 
				for (Long id : cons.valueIDs) {
					Constraint.ConstraintData sepcons = new Constraint.ConstraintData(cons.facetID);
					sepcons.valueIDs.add(id);
					rootFrame.historyPanel.addOperation(sepcons);
				}
			else 
				rootFrame.historyPanel.addOperation(cons);
		}
		
		filters.remove(f);
		this.remove(f);
		clearEmptyFacet();

		rootFrame.graphCanvas.performDataChangeToGraphCells(direction == FacetBrowser.Direction.VERTICAL, 
				i, GraphCanvas.GraphCellOp.REMOVE);
		layoutVisuals();
		rootFrame.graphCanvas.performAnimatedRendering();
		
		rootFrame.logger.logAction("filter-remove");
	}
	
	public void addNodesFilter(NodesFilter f, int index) {
		if (index == 0)
			index = 1;
		if (index > filters.size())
			index = filters.size();
		
		filters.add(index, f);
		this.add(f);	
		clearEmptyFacet();

		rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
				index, GraphCanvas.GraphCellOp.ADD);
		layoutVisuals();
		rootFrame.graphCanvas.performAnimatedRendering();
		
		rootFrame.logger.logAction("add filter");
	}
	
	public void updateNodesFitler(NodesFilter f) {
		int i = filters.indexOf(f);
		if (i != -1) {
			clearEmptyFacet();

			rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
					i, GraphCanvas.GraphCellOp.UPDATE);
			layoutVisuals();
			rootFrame.graphCanvas.performAnimatedRendering();
			
			rootFrame.logger.logAction("filter-modify");
		}
	}
	
	public void splitNodesFilter(NodesFilter f, Constraint.ConstraintData cdata) {
		// remove the old one
		int i = filters.indexOf(f);
		filters.remove(f);
		this.remove(f);
		rootFrame.graphCanvas.performDataChangeToGraphCells(direction == FacetBrowser.Direction.VERTICAL, 
				i, GraphCanvas.GraphCellOp.REMOVE);
		

		if (cdata.getFacetType() == Facet.FacetType.CATEGORICAL) {
			for (int j = cdata.valueIDs.size() - 1; j >= 0; j--) {
				NodesFilter nf = new NodesFilter(this, f);
				Constraint.ConstraintData ndata = new Constraint.ConstraintData(cdata.facetID);
				ndata.valueIDs.add(cdata.valueIDs.get(j));
				nf.replaceConstraint(ndata);
				
				filters.add(i, nf);
				this.add(nf);
				rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
						i, GraphCanvas.GraphCellOp.ADD);
			}

		}
		else {
			for (int j = cdata.toValue; j >= cdata.fromValue; j--) {
				NodesFilter nf = new NodesFilter(this, f);
				Constraint.ConstraintData ndata = new Constraint.ConstraintData(cdata.facetID);
				ndata.fromValue = ndata.toValue = j;
				nf.replaceConstraint(ndata);
				
				filters.add(i, nf);
				this.add(nf);
				rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
						i, GraphCanvas.GraphCellOp.ADD);
			}
		}
		
		layoutVisuals();
		rootFrame.graphCanvas.performAnimatedRendering();
		
		rootFrame.logger.logAction("filter-split");
	}
	
	public void childUpdated(NodesFilter f) {
		int i = filters.indexOf(f);
		if (i == -1)
			return;
		if (!f.isEmptyFilter()) {
			clearEmptyFacet();

			rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
					i, GraphCanvas.GraphCellOp.UPDATE);
			layoutVisuals();
			rootFrame.graphCanvas.performAnimatedRendering();
			
			rootFrame.logger.logAction("filter-modify");
		}
		else
			removeNodesFilter(f);
	}
	
	public void childRelayout(NodesFilter f) {
		int i = filters.indexOf(f);
		if (i == -1)
			return;
		rootFrame.graphCanvas.performDataChangeToGraphCells(this.direction == Direction.VERTICAL, 
				i, GraphCanvas.GraphCellOp.RELAYOUT);
		//layoutVisuals();
		rootFrame.graphCanvas.performAnimatedRendering();
		
		rootFrame.logger.logAction("filter-layout");
	}
	
	public void addConstraint(int filterIndex, Constraint.ConstraintData cons) {
		NodesFilter filter = filters.get(filterIndex);
		if (!facets.contains(Facet.availableFacets[cons.facetID])) {
			// add new constraints on the new facet
			facets.add(Facet.availableFacets[cons.facetID]);
			for (NodesFilter nf : filters) {
				nf.addConstraint(new Constraint.ConstraintData(cons.facetID));
			}
		}
		
		if (filterIndex == 0) {	// the "rest" filter
			NodesFilter nf = new NodesFilter(this);
			nf.replaceConstraint(cons);
			addNodesFilter(nf, 1);
		}
		else {			// existing filters
			filter.mergeConstraint(cons);
			updateNodesFitler(filter);
		}

		this.repaint();
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// events handlers
	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent arg0) {
		layoutVisuals();
	}

	@Override
	public void componentShown(ComponentEvent arg0) {}
	
	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {
		this.requestFocus();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods - tooling
	private boolean processDroppedConstraint(Point p, Constraint.ConstraintData cons) {
		for (int i = 0; i < filters.size(); i++) {
			NodesFilter filter = filters.get(i);
			Point p2 = new Point(p.x - filter.getX(), p.y - filter.getY());
			if (filter.filterVisual.getBounds().contains(p2)) {
				if (!facets.contains(Facet.availableFacets[cons.facetID])) {
					// add new constraints on the new facet
					facets.add(Facet.availableFacets[cons.facetID]);
					for (NodesFilter nf : filters) {
						nf.addConstraint(new Constraint.ConstraintData(cons.facetID));
					}
				}
				
				if (i == 0) {	// the "rest" filter
					NodesFilter nf = new NodesFilter(this);
					nf.replaceConstraint(cons);
					addNodesFilter(nf, 1);		
				}
				else {			// existing filters
					filter.mergeConstraint(cons);
					updateNodesFitler(filter);
				}

				this.repaint();
				return true;
			}
		}
		
		return false;
	}

	private void processDroppedFilter(Point p, NodesFilter f) {
		for (Constraint cons : f.getAllConstraints()) {
			if (!facets.contains(Facet.availableFacets[cons.getFacetID()])) {
				// add new constraints on the new facet
				facets.add(Facet.availableFacets[cons.getFacetID()]);
				for (NodesFilter nf : filters) {
					nf.addConstraint(new Constraint.ConstraintData(cons.getFacetID()));
				}
			}
		}
		
		for (int i = 1; i < filters.size(); i++) {
			NodesFilter filter = filters.get(i);
			Point p2 = new Point(p.x - filter.getX(), p.y - filter.getY());
			if (filter.filterVisual.getBounds().contains(p2)) {
				// merge filter
				for (Constraint cons : f.getAllConstraints()) {
//					if (!facets.contains(Facet.availableFacets[cons.getFacetID()])) {
//						// add new constraints on the new facet
//						facets.add(Facet.availableFacets[cons.getFacetID()]);
//						for (NodesFilter nf : filters) {
//							nf.addConstraint(new Constraint.ConstraintData(cons.getFacetID()));
//						}
//					}
					
					filter.mergeConstraint(cons.getConstraintData());
				}
				
				updateNodesFitler(filter);
				return;
			}
		}
		
		// insert filter
		NodesFilter nf = new NodesFilter(this);
		for (Constraint cons : f.getAllConstraints())
			nf.replaceConstraint(cons.getConstraintData());
		
		if (direction == Direction.HORIZONTAL) {
			double pos = p.x;
			for (int i = 0; i < filters.size(); i++) {
				NodesFilter filter = filters.get(i);
				pos = p.x - filter.getX() - filter.filterVisual.x - filter.filterVisual.width;
				if (pos < 0) {
					addNodesFilter(nf, i);
					return;
				}
			}
			
			addNodesFilter(nf, filters.size());
		}
		else {
			double pos = p.y;
			for (int i = 0; i < filters.size(); i++) {
				NodesFilter filter = filters.get(i);
				pos = p.y - filter.getY() - filter.filterVisual.y - filter.filterVisual.height;
				if (pos < 0) {
					addNodesFilter(nf, i);
					return;
				}
			}
			
			addNodesFilter(nf, filters.size());
		}
	}
	
	private void clearEmptyFacet() {
		boolean[] emptyFacet = new boolean[facets.size()];
		for (int i = 0; i < facets.size(); i++) {
			emptyFacet[i] = true;
			for (NodesFilter filter : filters) {
				if (!filter.getConstraint(facets.get(i).facetID).getConstraintData().isEmptyConstraint()) {
					emptyFacet[i] = false;
					break;
				}
			}
		}
		
		int deleted = 0;
		for (int i = 0; i < emptyFacet.length; i++) {
			if (emptyFacet[i]) {
				Facet f = facets.get(i - deleted);
				for (NodesFilter filter : filters) {
					filter.removeConstraint(f.facetID);
				}
				facets.remove(f);
				deleted += 1;
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	private class FacetBrowserDropHandler extends DropTargetAdapter implements DropTargetListener {

		private DropTarget dropTarget;

		public FacetBrowserDropHandler() {
			dropTarget = new DropTarget(FacetBrowser.this, DnDConstants.ACTION_COPY, this, true, null);
		}

		public void drop(DropTargetDropEvent event) {
			try {
				Transferable trans = event.getTransferable(); 
				if (event.isDataFlavorSupported(Constraint.dataFlavor)) {
					Constraint.ConstraintData cons = (Constraint.ConstraintData) trans.getTransferData(Constraint.dataFlavor);
					event.acceptDrop(DnDConstants.ACTION_COPY);
					
					boolean res = processDroppedConstraint(event.getLocation(), cons);
					
					if (res) {
						if (cons.getFacetType() == Facet.FacetType.CATEGORICAL) 
							for (Long id : cons.valueIDs) {
								Constraint.ConstraintData sepcons = new Constraint.ConstraintData(cons.facetID);
								sepcons.valueIDs.add(id);
								rootFrame.historyPanel.addOperation(sepcons);
							}
						else 
							rootFrame.historyPanel.addOperation(cons);
					}
					
					event.dropComplete(true);
					return;
				}
				else if (event.isDataFlavorSupported(NodesFilter.dataFlavor)) {
					NodesFilter f = (NodesFilter) trans.getTransferData(NodesFilter.dataFlavor);
					event.acceptDrop(DnDConstants.ACTION_MOVE);
					processDroppedFilter(event.getLocation(), f);
					event.dropComplete(true);
					return;
				}
				
				event.rejectDrop();
				
			} catch (Exception e) {
				e.printStackTrace();
				event.rejectDrop();
				rootFrame.showErrorMessage("Drag&Drop error:\n" + e.toString());
			}
		}
	}


}
