package pivotslice;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.microsoft.research.Author;
import com.microsoft.research.Keyword;
import com.microsoft.research.Publication;

public class Constraint extends JPanel implements ComponentListener, MouseListener, 
	DragGestureListener, Serializable {

	private static final long serialVersionUID = -7577713991679249734L;
	
	public static final int constraintSize = 24;
	public static final DataFlavor dataFlavor = new DataFlavor(ConstraintData.class, 
			ConstraintData.class.getSimpleName());
	
	public transient static PivotSlice rootFrame;
	
	private static final int margin = 2;
	private static final int buttonSize = 8;
	private static final double roundRadius = 20;
	private static final float alphaValue = 0.7f;
	private static final ImageIcon closeIcon = new ImageIcon(PivotSlice.class.getResource("/images/clear-icon.png"));
	
	public boolean isHighlighted = false;
	
	private ConstraintData constrData;
	private RoundRectangle2D.Double constraintVisual;
	private Ellipse2D.Double layoutIndicator;
	private JLabel clearButton = new JLabel(closeIcon);
	private ConstraintContent contentPanel;
	
	private final NodesFilter parentFilter;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public Constraint(int facetID, NodesFilter filter) {
		constrData = new ConstraintData(facetID);
		parentFilter = filter;
		commonConstructor();
	}
	
	public Constraint(ConstraintData data, NodesFilter filter) {
		constrData = data;
		parentFilter = filter;
		commonConstructor();
	}

	public int getFacetID() {
		return constrData.facetID;
	}
	
	public ConstraintData getConstraintData() {
		return constrData;
	}
	
	public void setConstraintData(ConstraintData data) {
		constrData  = data;
		contentPanel.updateAttributeStrs();
	}
	
	public void mergeConstraintData(ConstraintData newdata) {
		if (constrData.getFacetType() == Facet.FacetType.CATEGORICAL) {
			for (Long val : newdata.valueIDs) {
				if (!constrData.valueIDs.contains(val))
					constrData.valueIDs.add(val);
			}
		}
		else {		
			if (constrData.isEmptyConstraint())
				constrData = newdata;
			else {		// how to merge a numerical constraint?
				constrData.fromValue = constrData.fromValue > newdata.fromValue ? newdata.fromValue : constrData.fromValue;
				constrData.toValue = constrData.toValue > newdata.toValue ? constrData.toValue : newdata.toValue;
			}
		}
		contentPanel.updateAttributeStrs();
	}
	
	public boolean satisfyConstraint(Publication pub) {
		if (constrData.isEmptyConstraint())
			return true;
		else {
			if (constrData.getFacetType() == Facet.FacetType.CATEGORICAL) {
				for (Long id : Facet.getCategoricalFacetValueIDs(pub, constrData.facetID))
					if (constrData.valueIDs.contains(id))
						return true;
			}
			else {
				int val = Facet.getNumericalFacetValue(pub, constrData.facetID);
				return constrData.fromValue <= val && constrData.toValue >= val; 
			}
		}
		
		return false;
	}
	
	public void layoutVisual() {	
		Dimension dim = this.getSize();
		// background visual
		constraintVisual = new RoundRectangle2D.Double(margin, margin, 
				dim.getWidth() - 2 * margin, dim.getHeight() - 2 * margin, roundRadius, roundRadius);

		// button and panel
		if (parentFilter.direction == FacetBrowser.Direction.HORIZONTAL) {
			clearButton.setBounds(dim.width - buttonSize - margin, dim.height / 2 - buttonSize / 2, buttonSize, buttonSize);
			contentPanel.setBounds(buttonSize, margin, dim.width - 2 * buttonSize, dim.height - 2 * margin);
			layoutIndicator = new Ellipse2D.Double(margin, dim.height / 2 - buttonSize / 2, buttonSize, buttonSize);
		}
		else {
			clearButton.setBounds(dim.width / 2 - buttonSize / 2, margin, buttonSize, buttonSize);
			contentPanel.setBounds(margin, buttonSize, dim.width - 2 * margin, dim.height - 2 * buttonSize);
			layoutIndicator = new Ellipse2D.Double(dim.width / 2 - buttonSize / 2, dim.height - buttonSize - margin, buttonSize, buttonSize);
		}
		contentPanel.layoutLabels();
		
		//this.setToolTipText(getTooltipString());
		
		this.repaint();
	}
	
	public void childUpdated() {
		contentPanel.updateAttributeStrs();
		layoutVisual();
		parentFilter.childUpdated(this);
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// events handlers
	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent arg0) {
		layoutVisual();
	}

	@Override
	public void componentShown(ComponentEvent arg0) {}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() > 2)
			return;
		
		if (e.getClickCount() == 2) {
			parentFilter.splitConstraint(this);
			return;
		}
		
		Point p = e.getPoint();
		if (clearButton.isVisible() && clearButton.getBounds().contains(p)) {
			rootFrame.historyPanel.addOperation(constrData);
			setConstraintData(new Constraint.ConstraintData(constrData.facetID));
			layoutVisual();
			parentFilter.childUpdated(this);
		}
		else if (layoutIndicator.getBounds().contains(p)) {
			//parentFilter.preLayoutFacetID = parentFilter.layoutFacetID;
			
			if (parentFilter.getLayoutFacet()!= null && 
					parentFilter.getLayoutFacet().facetID == constrData.facetID) 
				//parentFilter.layoutFacetID = -1;	
				parentFilter.setLayoutFacet(-1);
			else 
				//parentFilter.layoutFacetID = constrData.facetID;
				parentFilter.setLayoutFacet(constrData.facetID);
			
			parentFilter.childRelayout(this);
			this.repaint();
		}
	}

	@Override
	public void mouseEntered(MouseEvent evt) {
		this.setToolTipText(getTooltipString());
		isHighlighted = true;
		if (!constrData.isEmptyConstraint())
			clearButton.setVisible(true);
		parentFilter.dispatchEvent(evt);
		//parentFilter.isHighlighted = true;
		//parentFilter.repaint();
	}

	@Override
	public void mouseExited(MouseEvent evt) {
		this.setToolTipText(null);
		isHighlighted = false;
		clearButton.setVisible(false);
		parentFilter.dispatchEvent(evt);
		//parentFilter.isHighlighted = false;
		//parentFilter.repaint();
	}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		this.requestFocus();
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (constraintVisual == null)
			return;
		
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		Color c = Facet.availableFacets[constrData.facetID].facetColor;
		if (constrData.isEmptyConstraint()) {
			if (isHighlighted) {
				g2.setColor(Color.lightGray);
				g2.fill(constraintVisual);
			}
			g2.setColor(c);
			g2.draw(constraintVisual);
		}
		else {
			if (isHighlighted) {
				g2.setColor(c);
				g2.fill(constraintVisual);
				g2.setColor(Color.black);
				g2.draw(constraintVisual);

				if (parentFilter.getLayoutFacet()!= null && 
						parentFilter.getLayoutFacet().facetID == constrData.facetID)
					g2.fill(layoutIndicator);
				else
					g2.draw(layoutIndicator);
			}
			else {
				float[] rgb = c.getColorComponents(null);
				g2.setColor(new Color(rgb[0], rgb[1], rgb[2], alphaValue));
				g2.fill(constraintVisual);
				
				g2.setColor(Color.black);
				if (parentFilter.getLayoutFacet()!= null && 
						parentFilter.getLayoutFacet().facetID == constrData.facetID)
					g2.fill(layoutIndicator);
			}	
		}
		
		g2.dispose();
	}
	
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		rootFrame.glassPane.setImage(PivotSlice.getScreenShot(this));
		if (dge.getDragAction() == DnDConstants.ACTION_COPY) {
			dge.startDrag(DragSource.DefaultCopyDrop, new TransferableConstraint(constrData));
		}
		else {
			dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(constrData));
			setConstraintData(new ConstraintData(constrData.facetID));
			childUpdated();
		}
		
		rootFrame.logger.logAction("filter-drag constraint");
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods
	private void commonConstructor() {
		this.setOpaque(false);
		this.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
		this.setLayout(null);
		this.setFocusable(true);
		
		contentPanel = new ConstraintContent(this, parentFilter.direction == FacetBrowser.Direction.VERTICAL);
		this.add(contentPanel);
		clearButton.setVisible(false);
		//clearButton.setToolTipText("clear all");
		this.add(clearButton);
		
		this.addComponentListener(this);
		this.addMouseListener(this);
		this.setFocusable(true);
		
		DragSource ds = new DragSource();
		ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
		ds.addDragSourceMotionListener(rootFrame.dragListener);
		ds.addDragSourceListener(rootFrame.dragListener);
	}
	
	private String getTooltipString() {
		StringBuilder sb = new StringBuilder("<html>");
		sb.append(Facet.availableFacets[constrData.facetID].facetName + ": ");
		if (Facet.getFacetType(constrData.facetID) == Facet.FacetType.NUMERICAL) {
			sb.append(String.format("<br/>%d-%d</html>", constrData.fromValue, constrData.toValue));
		}
		else {
			for (Long id : constrData.valueIDs) {
				sb.append("<br/>" + Facet.getCategoricalFacetString(id, constrData.facetID) + ";");
			}
			sb.append("</html>");
		}
		
		return sb.toString();
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	public static class ConstraintData implements Serializable {
		private static final long serialVersionUID = 629844575511161209L;
		
		public int facetID;
		public ArrayList<Long> valueIDs = new ArrayList<Long>();
		public int fromValue = -1;
		public int toValue = -1;
		
		public ConstraintData(int fid) {
			facetID = fid;
		}
		
		public ConstraintData(ConstraintData cdata) {
			facetID = cdata.facetID;
			fromValue = cdata.fromValue;
			toValue = cdata.toValue;
			for (Long id : cdata.valueIDs)
				valueIDs.add(id);
		}

		public boolean equals(ConstraintData cdata) {
			if (facetID == cdata.facetID) {
				if (getFacetType() == Facet.FacetType.CATEGORICAL) {
					if (valueIDs.size() != cdata.valueIDs.size())
						return false;
					boolean isdiffer = true;
					
					for (int i = 0; i < valueIDs.size(); i++) {
						if (!cdata.valueIDs.contains(valueIDs.get(i))) {
							isdiffer = false;
							break;
						}
					}
					return isdiffer;
				}
				else {
					return fromValue == cdata.fromValue && toValue == cdata.toValue;
				}
			}
			
			return false;
		}
		
		public boolean isEmptyConstraint() {
			if (getFacetType() == Facet.FacetType.CATEGORICAL)
				return valueIDs.isEmpty();
			else
				return fromValue == -1 && toValue == -1; 
		}
		
		public Facet.FacetType getFacetType() {
			return Facet.getFacetType(facetID);
		}
		
		public List<String> getValueStrings() {
			List<String> strings = new LinkedList<String>();
			for (Long id : valueIDs) {
				strings.add(Facet.getCategoricalFacetString(id, facetID));
			}
			return strings;
		}
	}
	
	public static class TransferableConstraint implements Transferable {
		private ConstraintData data;
		
		public TransferableConstraint(ConstraintData consdata) {
			data = consdata;
		}
		
		public TransferableConstraint(int fid, Long value) {
			data = new ConstraintData(fid);
			data.valueIDs.add(value);
		}
		
		public TransferableConstraint(int fid, List<Long> values) {
			data = new ConstraintData(fid);
			data.valueIDs.addAll(values);
		}
		
		public TransferableConstraint(int fid, int fromValue, int toValue) {
			data = new ConstraintData(fid);
			data.fromValue = fromValue;
			data.toValue = toValue;
		}
		
		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor.equals(dataFlavor))
				return data;
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



