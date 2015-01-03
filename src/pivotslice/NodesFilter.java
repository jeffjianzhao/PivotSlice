package pivotslice;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.microsoft.research.Publication;

public class NodesFilter extends JPanel implements ComponentListener, MouseListener, 
	DragGestureListener, Serializable {
	
	private static final long serialVersionUID = 2153359325407205584L;
	
	public static final Color filterColor1 = new Color(150, 150, 150, 30);
	public static final Color filterColor2 = new Color(100, 100, 100, 80);
	public static final int collapsedSize = 40;
	public static final int baseSize = 100;
	private static final Font font = new Font("SansSerif", Font.PLAIN, 9);
	public static final DataFlavor dataFlavor = new DataFlavor(NodesFilter.class, 
			NodesFilter.class.getSimpleName());
	
	public transient static PivotSlice rootFrame;
	
	private static final int margin = 10;
	private static final double roundRadius = 10;
	private static ImageIcon closeIcon = new ImageIcon(PivotSlice.class.getResource("/images/close-icon.png"));
	private static ImageIcon resizeIcony = new ImageIcon(PivotSlice.class.getResource("/images/resize-icon1.png"));
	private static ImageIcon resizeIconx = new ImageIcon(PivotSlice.class.getResource("/images/resize-icon2.png"));
	private static Color buttonColor = new Color(233, 175, 175);
	
	private int layoutFacetID = -1;
	private int preLayoutFacetID = -1;
	public boolean isHighlighted = false;
	//public boolean isSelected = false;
	public boolean isCollapsed = false;
	public FacetBrowser.Direction direction;
	public transient FacetBrowser parentBrowser;
	public RoundRectangle2D.Double filterVisual;
	
	private ArrayList<Constraint> constraints = new ArrayList<Constraint>();
	private JLabel resizeButton;
	private JLabel closeButton;
	private RotateLabel layoutLabel;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public NodesFilter(FacetBrowser parent) {
		parentBrowser = parent;
		direction = parent.direction;
		
		this.setFocusable(true);
		this.setOpaque(false);
		this.setLayout(null);
		this.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
		this.addComponentListener(this);
		this.addMouseListener(this);
		
		for (Facet f : parentBrowser.getFacets()) {
			Constraint cons = new Constraint(f.facetID, this);
			constraints.add(cons);
			this.add(cons);
		}
		
		if (direction == FacetBrowser.Direction.HORIZONTAL) 
			layoutLabel = new RotateLabel(RotateLabel.Direction.HORIZONTAL);
		else
			layoutLabel = new RotateLabel(RotateLabel.Direction.VERTICAL_UP);
		layoutLabel.setFont(font);
		layoutLabel.setHorizontalAlignment(JLabel.CENTER);
		layoutLabel.setVerticalAlignment(JLabel.CENTER);
		this.add(layoutLabel);
		
		MouseAdapter listener = new ButtonMouseListener();
		
		closeButton = new JLabel(closeIcon);
		closeButton.addMouseListener(listener);
		closeButton.setOpaque(true);
		closeButton.setToolTipText("Remove");
		
		if (direction == FacetBrowser.Direction.HORIZONTAL) 
			resizeButton = new JLabel(resizeIconx);
		else 
			resizeButton = new JLabel(resizeIcony);
		resizeButton.addMouseListener(listener);
	    resizeButton.setOpaque(true);
	    resizeButton.setToolTipText("Minimize/Maximize");
	    
	    this.add(closeButton);
		this.add(resizeButton);
		
		DragSource ds = new DragSource();
		ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
		ds.addDragSourceMotionListener(rootFrame.dragListener);
		ds.addDragSourceListener(rootFrame.dragListener);
	}
	
	public NodesFilter(FacetBrowser parent, NodesFilter f) {
		this(parent);
		for (Constraint cons : f.getAllConstraints())
			mergeConstraint(new Constraint.ConstraintData(cons.getConstraintData()));
	}
	
	public boolean isEmptyFilter() {
		boolean result = true;
		for (Constraint cons : constraints) {
			if (!cons.getConstraintData().isEmptyConstraint()) {
				result = false;
				break;
			}
		}
		return result;
	}
	
	public Constraint getConstraint(int facetID) {
		for (Constraint cons : constraints) {
			if (cons.getFacetID() == facetID) {
				return cons;
			}			
		}
		return null;
	}
	
	public ArrayList<Constraint> getAllConstraints() {
		return constraints;
	}
	
	public void addConstraint(Constraint.ConstraintData cdata) {
		Constraint cons = new Constraint(cdata, this);
		constraints.add(cons);
		this.add(cons);
	}
	
	public void replaceConstraint(Constraint.ConstraintData cdata) {
		for (Constraint cons : constraints) {
			if (cons.getFacetID() == cdata.facetID) {
				cons.setConstraintData(cdata);
				break;
			}
		}
	}
	
	public void mergeConstraint(Constraint.ConstraintData cdata) {
		for (Constraint cons : constraints) {
			if (cons.getFacetID() == cdata.facetID) {
				cons.mergeConstraintData(cdata);
				break;
			}
		}
	}
	
	public void removeConstraint(int facetID) {
		for (Constraint cons : constraints) {
			if (cons.getFacetID() == facetID) {
				this.remove(cons);
				constraints.remove(cons);
				if (facetID == layoutFacetID) {
					preLayoutFacetID = layoutFacetID;
					layoutFacetID = -1;
				}
				break;
			}			
		}
	}
	
	public boolean satisfyConstraint(Publication pub) {
		for (Constraint cons : constraints) {
			if (!cons.satisfyConstraint(pub))
				return false;
		}
		
		return true;
	}
	
	public void splitConstraint(Constraint cons) {
		Constraint.ConstraintData cdata = cons.getConstraintData();
		if ((cdata.getFacetType() == Facet.FacetType.CATEGORICAL && cdata.valueIDs.size() <= 1) || 
				(cdata.getFacetType() == Facet.FacetType.NUMERICAL && cdata.toValue - cdata.fromValue <= 1))
			return;

		parentBrowser.splitNodesFilter(this, cdata);
	}
	
	public void layoutVisuals() {
		if (direction == FacetBrowser.Direction.HORIZONTAL) {
			closeButton.setBounds(0, 0, 10, 10);
			resizeButton.setBounds(0, 10, 10, 10);
			layoutLabel.setBounds(0, 0, this.getWidth(), 10);
		}
		else {
			closeButton.setBounds(0, this.getHeight() - 10, 10, 10);
			resizeButton.setBounds(10, this.getHeight() - 10, 10, 10);
			layoutLabel.setBounds(this.getWidth() - 10, 0, 10, this.getHeight());
		}
		
		ArrayList<Facet> facets = parentBrowser.getFacets();
		if (facets.size() == 0) {
			this.repaint();
			return;
		}
		
		if (direction == FacetBrowser.Direction.HORIZONTAL) {
			int w = this.getWidth() - margin * 2;
			int h = (int)((this.getHeight() - margin * 2.0) / facets.size());
			h = h < Constraint.constraintSize ? h : Constraint.constraintSize;
			
			for (int i = 0; i < facets.size(); i++) {
				for (Constraint cons : constraints) {
					if (cons.getFacetID() == facets.get(i).facetID) {
						cons.setBounds(margin, margin + i * h, w, h);
						cons.layoutVisual();
						break;
					}
				}
			}
		}
		else {
			int w = (int)((this.getWidth() - margin * 2.0) / facets.size());
			int h = this.getHeight() - margin * 2;
			w = w < Constraint.constraintSize ? w : Constraint.constraintSize;
			
			for (int i = 0; i < facets.size(); i++) {
				for (Constraint cons : constraints) {
					if (cons.getFacetID() == facets.get(i).facetID) {
						cons.setBounds(margin + i * w, margin, w, h);
						cons.layoutVisual();
						break;
					}
				}
			}
		}
		
		this.repaint();
	}
	
	public void childUpdated(Constraint cons) {
		parentBrowser.childUpdated(NodesFilter.this);
	}
	
	public void childRelayout(Constraint cons) {
		parentBrowser.childRelayout(NodesFilter.this);
	}
	
	public Facet getPreLayoutFacet() {
		if (preLayoutFacetID == -1)
			return null;
		else 
			return Facet.availableFacets[preLayoutFacetID];
	}
	
	public void setLayoutFacet(int fid) {
		preLayoutFacetID = layoutFacetID;
		layoutFacetID = fid;
		if (layoutFacetID == -1)
			layoutLabel.setText("");
		else
			layoutLabel.setText(Facet.availableFacets[layoutFacetID].facetName);
	}
	
	public Facet getLayoutFacet() {
		if (layoutFacetID == -1)
			return null;
		else 
			return Facet.availableFacets[layoutFacetID];
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// events handlers
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		if (parentBrowser.getNodesFilters().indexOf(this) == 0)
			return;
		rootFrame.glassPane.setImage(PivotSlice.getScreenShot(this));
		if (dge.getDragAction() == DnDConstants.ACTION_COPY) {
			dge.startDrag(DragSource.DefaultCopyDrop, new TransferableNodesFilter(this));	
		}
		else {
			dge.startDrag(DragSource.DefaultMoveDrop, new TransferableNodesFilter(this));
			parentBrowser.removeNodesFilter(this);
		}
		
		rootFrame.logger.logAction("filter-drag filter");
	}
	
	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent arg0) {	
		filterVisual = new RoundRectangle2D.Double(margin, margin, this.getWidth() - 2 * margin, 
				this.getHeight() - 2 * margin, roundRadius, roundRadius);
		layoutVisuals();
	}

	@Override
	public void componentShown(ComponentEvent arg0) {}

	@Override
	public void mouseClicked(MouseEvent me) {
//		if (me.isControlDown()) {
//			isSelected = !isSelected;
//			this.repaint();
//		}
//		else {
//			parentBrowser.clearFilterSelection();
//			isSelected = true;
//			this.repaint();
//		}
	}

	@Override
	public void mouseEntered(MouseEvent me) {
		isHighlighted = true;
		this.repaint();
			
		rootFrame.logger.logAction("filter-hover over");
	}

	@Override
	public void mouseExited(MouseEvent me) {
		isHighlighted = false;
		this.repaint();
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		this.requestFocus();
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		this.requestFocus();
	}
		
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (filterVisual == null)
			return;
		
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		if (parentBrowser.getNodesFilter(0) == this)
			g2.setColor(filterColor1);
		else
			g2.setColor(filterColor2);
		g2.fill(filterVisual);
		if (isHighlighted) {
			g2.setColor(Color.black);
			g2.draw(filterVisual);
		}
//		else if (isSelected) {
//			g2.setColor(Color.red);
//			g2.draw(filterVisual);
//		}
		
		g2.dispose();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	private class ButtonMouseListener extends MouseAdapter implements Serializable {
		private static final long serialVersionUID = 319990840352135928L;

		@Override
		public void mouseEntered(MouseEvent evt) {
			JLabel label = (JLabel)evt.getSource();
			label.setBackground(buttonColor);
		}

		@Override
		public void mouseExited(MouseEvent evt) {
			JLabel label = (JLabel)evt.getSource();
			label.setBackground(Color.WHITE);
		}

		@Override
		public void mouseClicked(MouseEvent evt) {
			if (evt.getSource() == resizeButton) {
				parentBrowser.resizeNodesFilter(NodesFilter.this);
			}
			else if (evt.getSource() == closeButton) {
				parentBrowser.removeNodesFilter(NodesFilter.this);
			}
		}
		
	}
	
	public static class TransferableNodesFilter implements Transferable {
		private NodesFilter filter;
		
		public TransferableNodesFilter(NodesFilter f) {
			filter = f;
			/*
			for (Facet fa : Facet.availableFacets)
				if (filter.getConstraint(fa.facetID).getConstraintData().isEmptyConstraint())
					filter.removeConstraint(fa.facetID);
			*/
		}
		
		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor.equals(dataFlavor))
				return filter;
			else
				throw new UnsupportedFlavorException(flavor);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {dataFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return flavor.equals(dataFlavor);
		}
		
	}

}
