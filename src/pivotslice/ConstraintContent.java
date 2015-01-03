package pivotslice;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import pivotslice.Constraint.TransferableConstraint;


public class ConstraintContent extends JPanel implements Serializable {
	
	private static final long serialVersionUID = 8860740501698581702L;
	
	private static final Font font = new Font("SansSerif", Font.PLAIN, 9);
	private static final int labelSize = 14;
	private static final int buttonSize = 8;
	private static final float contentRatio = 0.9f;
	private static ImageIcon upIcon = new ImageIcon(PivotSlice.class.getResource("/images/up-icon.png"));
	private static ImageIcon downIcon = new ImageIcon(PivotSlice.class.getResource("/images/down-icon.png"));
	private static ImageIcon leftIcon = new ImageIcon(PivotSlice.class.getResource("/images/left-icon.png"));
	private static ImageIcon rightIcon = new ImageIcon(PivotSlice.class.getResource("/images/right-icon.png"));
	private static ImageIcon removeIcon = new ImageIcon(PivotSlice.class.getResource("/images/remove-icon.png"));
	
	private final boolean needsRotate;
	private final Constraint parentConstr;
	
	private ContentLabel attributeLabel;
	private ContentLabel fromLabel, toLabel;
	private ArrayList<String> attributeStrs = new ArrayList<String>();
	private int showIndex = 0;
	
	public transient static PivotSlice rootFrame;
	
	public ConstraintContent(Constraint cons, boolean rotate) {
		parentConstr = cons;
		needsRotate = rotate;
		RotateLabel.Direction dir = needsRotate ? RotateLabel.Direction.VERTICAL_UP : RotateLabel.Direction.HORIZONTAL;
		
		if (cons.getConstraintData().getFacetType() == Facet.FacetType.NUMERICAL) {
			fromLabel = new ContentLabel(dir);
			toLabel = new ContentLabel(dir);
			this.add(fromLabel);
			this.add(toLabel);
		}
		else {
			attributeLabel =  new ContentLabel(dir);
			this.add(attributeLabel);
		}
		
		updateAttributeStrs();
		
		this.setLayout(null);
		this.setOpaque(false);
	}
	
	public void updateAttributeStrs() {
		Constraint.ConstraintData cdata = parentConstr.getConstraintData();
		if (cdata.isEmptyConstraint())
			return;
		
		attributeStrs.clear();
		if (cdata.getFacetType() == Facet.FacetType.NUMERICAL) {
			attributeStrs.add(String.format("%d", cdata.fromValue));
			attributeStrs.add(String.format("%d", cdata.toValue));
		}
		else {
			attributeStrs.addAll(cdata.getValueStrings());
		}
		
		updateLabels();
	}

	public void layoutLabels() {		
		Constraint.ConstraintData cdata = parentConstr.getConstraintData();		
		Dimension dim = this.getSize();
		if (cdata.isEmptyConstraint() || (needsRotate && dim.height < NodesFilter.collapsedSize) || 
				(!needsRotate && dim.width < NodesFilter.collapsedSize)) {
			for (Component cmp : this.getComponents())
				cmp.setVisible(false);
			return;
		}
		
		for (Component cmp : this.getComponents())
			cmp.setVisible(true);
		
		if (cdata.getFacetType() == Facet.FacetType.NUMERICAL) {
			if (needsRotate) {
				int labelHeight = (int)(dim.height * contentRatio / 2);
				toLabel.setBounds(dim.width / 2 - labelSize / 2,  dim.height / 2 - labelHeight - 2, labelSize, labelHeight);
				fromLabel.setBounds(dim.width / 2 - labelSize / 2, dim.height / 2 + 2, labelSize, labelHeight);
			}
			else {
				int labelWidth = (int)(dim.width * contentRatio / 2);	
				fromLabel.setBounds(dim.width / 2 - labelWidth - 2, dim.height / 2 - labelSize / 2, labelWidth, labelSize);
				toLabel.setBounds(dim.width / 2, dim.height / 2 - labelSize / 2, labelWidth, labelSize);
			}
		}
		else {
			if (needsRotate) {
				int labelHeight = (int)(dim.height * contentRatio);
				attributeLabel.setBounds(dim.width / 2 - labelSize / 2, dim.height / 2 - labelHeight / 2, labelSize, labelHeight);
			}
			else {
				int labelWidth = (int)(dim.width * contentRatio);	
				attributeLabel.setBounds(dim.width / 2 - labelWidth / 2, dim.height / 2 - labelSize / 2, labelWidth, labelSize);
				
			}
		}
		
		this.repaint();
	}
	
	public void updateLabels() {
		if (parentConstr.getConstraintData().getFacetType() == Facet.FacetType.NUMERICAL) {
			fromLabel.setText(attributeStrs.get(0));
			toLabel.setText(attributeStrs.get(1));
		}
		else {
			showIndex = showIndex < 0 ? 0 : showIndex;
			showIndex = showIndex < attributeStrs.size() ? showIndex : attributeStrs.size() - 1;
			attributeLabel.setText((showIndex + 1) + "/" + attributeStrs.size() + " " + attributeStrs.get(showIndex));
		}
		
		this.repaint();
	}
	
	private void prevPressed(ContentLabel source) {
		if (source == attributeLabel) {
			showIndex--;
			updateLabels();
		}
		else if (source == fromLabel) {
			if (parentConstr.getConstraintData().fromValue - 1 >= 0) {
				parentConstr.getConstraintData().fromValue--;
				parentConstr.childUpdated();
			}
		}
		else if (source == toLabel) {
			if (parentConstr.getConstraintData().toValue - 1 >= 0 
					&& parentConstr.getConstraintData().toValue - 1 >=  parentConstr.getConstraintData().fromValue) {
				parentConstr.getConstraintData().toValue--;
				parentConstr.childUpdated();
			}
		}
	}
	
	private void nextPressed(ContentLabel source) {
		if (source == attributeLabel) {
			showIndex++;
			updateLabels();
		}
		else if (source == fromLabel) {
			if (parentConstr.getConstraintData().fromValue + 1 <= parentConstr.getConstraintData().toValue) {
				parentConstr.getConstraintData().fromValue++;
				parentConstr.childUpdated();
			}
		}
		else if (source == toLabel) {
			parentConstr.getConstraintData().toValue++;
			parentConstr.childUpdated();
		}
	}
	
	private void contentEdited(ContentLabel source) {
		if (source == attributeLabel)
			return;
		
		String result = JOptionPane.showInputDialog(Constraint.rootFrame, "Please input the value:");
		int value = 0;
		try {
			value = Integer.parseInt(result);
		}
		catch (NumberFormatException e) {
			return;
		}
		
		if (source == fromLabel && value >= 0 && value <= parentConstr.getConstraintData().toValue) {
			parentConstr.getConstraintData().fromValue = value;
			parentConstr.childUpdated();
		}
		else if (source == toLabel && value >= parentConstr.getConstraintData().fromValue) {
			parentConstr.getConstraintData().toValue = value;
			parentConstr.childUpdated();
		}
	}
	
	private void removePressed() {
		Constraint.ConstraintData cdata = new Constraint.ConstraintData(parentConstr.getFacetID());
		cdata.valueIDs.add(parentConstr.getConstraintData().valueIDs.get(showIndex));		
		Constraint.rootFrame.historyPanel.addOperation(cdata);
		
		parentConstr.getConstraintData().valueIDs.remove(showIndex);
		parentConstr.childUpdated();
	}
	
	
	class ContentLabel extends JPanel implements ComponentListener, MouseListener, Serializable, DragGestureListener {

		private static final long serialVersionUID = 2555409040989392097L;
		
		private RotateLabel label;
		private JLabel prevButton;
		private JLabel nextButton;
		private JLabel removeButton;
	
		public ContentLabel(RotateLabel.Direction dir) {
			label = new RotateLabel(dir);
			label.setFont(font);
			label.setHorizontalAlignment(JLabel.CENTER);
			label.setVerticalAlignment(JLabel.CENTER);
			
			if (needsRotate) {
				prevButton = new JLabel(downIcon);
				nextButton = new JLabel(upIcon);
			}
			else {
				prevButton = new JLabel(leftIcon);
				nextButton = new JLabel(rightIcon);
			}
			
			prevButton.setVisible(false);
			nextButton.setVisible(false);
			
			this.add(prevButton);
			this.add(label);
			this.add(nextButton);
			
			if (parentConstr.getConstraintData().getFacetType() == Facet.FacetType.CATEGORICAL) {
				removeButton = new JLabel(removeIcon);
				removeButton.setVisible(false);
				this.add(removeButton);
				/*
				removeButton.setToolTipText("remove attribute");
				prevButton.setToolTipText("previous attribute");
				nextButton.setToolTipText("next attribute");
				*/
			}
			/*
			else {
				prevButton.setToolTipText("decrease value");
				nextButton.setToolTipText("increase value");
			}*/
			
			this.setLayout(null);
			this.setOpaque(false);
			this.addComponentListener(this);
			this.addMouseListener(this);
			
			DragSource ds = new DragSource();
			ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
			ds.addDragSourceMotionListener(rootFrame.dragListener);
			ds.addDragSourceListener(rootFrame.dragListener);
		}
		
		public void setText(String text) {
			label.setText(text);
			this.repaint();
		}

		@Override
		public void componentHidden(ComponentEvent arg0) {}

		@Override
		public void componentMoved(ComponentEvent arg0) {}

		@Override
		public void componentResized(ComponentEvent arg0) {
			Dimension dim = this.getSize();
			if (needsRotate) {
				prevButton.setBounds(dim.width / 2 - buttonSize / 2, dim.height - buttonSize, buttonSize, buttonSize);
				label.setBounds(0, buttonSize, dim.width, dim.height - 2 * buttonSize);
				nextButton.setBounds(dim.width / 2 - buttonSize / 2, 0, buttonSize, buttonSize);
				if (removeButton != null)
					removeButton.setBounds(dim.width / 2 - buttonSize / 2, buttonSize, buttonSize, buttonSize);
			}
			else {
				prevButton.setBounds(0, dim.height / 2 - buttonSize / 2, buttonSize, buttonSize);
				label.setBounds(buttonSize, 0, dim.width - 2 * buttonSize, dim.height);
				nextButton.setBounds(dim.width - buttonSize, dim.height / 2 - buttonSize / 2, buttonSize, buttonSize);
				if (removeButton != null)
					removeButton.setBounds(dim.width - 2 * buttonSize, dim.height / 2 - buttonSize / 2, buttonSize, buttonSize);
			}
			
			this.repaint();
		}

		@Override
		public void componentShown(ComponentEvent arg0) {}

		@Override
		public void mouseClicked(MouseEvent me) {
			if (me.getButton() != MouseEvent.BUTTON1 || me.getClickCount() > 2)
				return;
			
			if (me.getClickCount() == 2) {
				contentEdited(this);
				return;
			}
			
			Point p = me.getPoint();
			if (prevButton.getBounds().contains(p))
				prevPressed(this);
			else if (nextButton.getBounds().contains(p))
				nextPressed(this);	
			else if (removeButton != null && removeButton.getBounds().contains(p))
				removePressed();
		}

		@Override
		public void mouseEntered(MouseEvent arg0) {
			//this.setBorder(emBorder);
			prevButton.setVisible(true);
			nextButton.setVisible(true);
			if (removeButton != null)
				removeButton.setVisible(true);
			parentConstr.mouseEntered(arg0);
		}

		@Override
		public void mouseExited(MouseEvent arg0) {
			//this.setBorder(null);
			prevButton.setVisible(false);
			nextButton.setVisible(false);
			if (removeButton != null)
				removeButton.setVisible(false);
			parentConstr.mouseExited(arg0);
		}

		@Override
		public void mousePressed(MouseEvent arg0) {}

		@Override
		public void mouseReleased(MouseEvent arg0) {}
		
		@Override
		public void dragGestureRecognized(DragGestureEvent dge) {
			rootFrame.glassPane.setImage(PivotSlice.getScreenShot(this));
			Point p = dge.getDragOrigin();
			Constraint.ConstraintData cdata = parentConstr.getConstraintData();
			
			if (dge.getDragAction() == DnDConstants.ACTION_COPY) {
				if (attributeLabel == this) 
					dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID, 
							cdata.valueIDs.get(showIndex)));	
				else  if (fromLabel == this) 
					dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID,
							cdata.fromValue, cdata.fromValue));
				else if (toLabel == this) 
					dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID,
							cdata.toValue, cdata.toValue));	
			}
			else {
				if (attributeLabel == this) {
					dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID, 
							cdata.valueIDs.get(showIndex)));
					removePressed();
				}
				else if (cdata.fromValue != cdata.toValue) {
					if (fromLabel == this) {	
						dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID,
								cdata.fromValue, cdata.fromValue));
						nextPressed(this);	
					}
					else if (toLabel == this) {
						dge.startDrag(DragSource.DefaultMoveDrop, new TransferableConstraint(cdata.facetID,
								cdata.toValue, cdata.toValue));
						prevPressed(this);
					}
				}
			}
			
			rootFrame.logger.logAction("filter-drag attribute");
		}
		
	}

}
