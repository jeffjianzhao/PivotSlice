package pivotslice;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import com.microsoft.research.Author;
import com.microsoft.research.Conference;
import com.microsoft.research.Journal;
import com.microsoft.research.Keyword;
import com.microsoft.research.Publication;

public class InfoPanel extends JPanel implements DragGestureListener, MouseListener, KeyListener  {	
	private static final int maxDisplayNum = 15;
	private static final Font font = new Font("SansSerif", Font.BOLD, 9);
	private static final Border border = BorderFactory.createLineBorder(Color.BLACK);
	
	private JComboBox selector = new JComboBox();
	private JLabel title = new JLabel();
	private boolean isAddMultiple = false;
	private JComponent[] components = new JComponent[Facet.availableFacets.length];	
	private ArrayList<Publication> publications = new ArrayList<Publication>();
	private ArrayList<Object> aggregations = new ArrayList<Object>();
	private NodesFilter filterx, filtery;
	
	private final PivotSlice rootFrame;
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public InfoPanel(PivotSlice frame) {
		rootFrame = frame;
		
		this.setPreferredSize(new Dimension(160, 600));
		this.setLayout(new GridBagLayout());
		
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.PAGE_START;
		c.ipadx = 2;
		c.insets = new Insets(2,2,2,2);
		
		this.add(selector, c);
		selector.addActionListener(new SelectorListener());
		
		this.add(title, c);
		title.setOpaque(true);
		title.setBorder(border);
		title.setFont(font);
		title.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Pattern p = Pattern.compile("<a href=\"([\\w/:._~%-+&#?!=()@]*)\">");
				Matcher m = p.matcher(title.getText());
				try {
					if (m.find()) {
						Desktop.getDesktop().browse(new URI(m.group(1)));
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});

		for (int i = 0; i < Facet.availableFacets.length; i++) {
			if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) {
				TagLabel label = new TagLabel("", i);
				//JLabel label = new JLabel();
				//setLabelAttributes(label, i);
				components[i] = label;
				this.add(label, c);
			}
			else {
				JPanel panel = new JPanel();
				setPanelAttributes(panel);
				components[i] = panel;
				this.add(panel, c);
			}
		}
		
		c.weighty = 1;
		this.add(new JPanel(), c);	

		this.setVisible(false);
		this.setFocusable(true);
		this.addMouseListener(this);
		this.addKeyListener(this);
		
		DragSource ds = new DragSource();
		ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
		ds.addDragSourceMotionListener(rootFrame.dragListener);
		ds.addDragSourceListener(rootFrame.dragListener);
	}

	public void displayPublication(Set<Publication> pubs, boolean isSelecting) {
		publications.clear();
		publications.addAll(pubs);
		selector.removeAllItems();	
		
		GraphCell cell = null;
		if (isSelecting)
			cell = rootFrame.graphCanvas.getSelectedCell();
		else
			cell = rootFrame.graphCanvas.getHoveredCell();
		if (cell != null) {
			filterx = rootFrame.facetBrowserX.getNodesFilters().get(cell.gridx);
			filtery = rootFrame.facetBrowserY.getNodesFilters().get(cell.gridy);
		}
		
		if (publications.size() == 0) {
			selector.setSelectedIndex(-1);
			this.setVisible(false);
			return;
		}
		
		this.setVisible(true);
		
		for(int i = 0; i < pubs.size(); i++)
			selector.addItem(publications.get(i).getTitle());
		if (publications.size() > 1) {
			selector.addItem("All publications");	
			performAggregation();
		}
		
		selector.setSelectedIndex(-1);
		if (publications.size() > 1) 
			selector.setSelectedIndex(publications.size());
		else
			selector.setSelectedIndex(0);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// event handlers
	@Override
	public void mouseClicked(MouseEvent me) {
		if (me.getButton() != MouseEvent.BUTTON1 || me.getClickCount() != 1)
			return;

		if (!isAddMultiple)
			return;
		
		Point p = me.getPoint();
		for (int i = 0; i < components.length; i++)  {
			if (components[i].getBounds().contains(p) 
					&& Facet.availableFacets[i].facetType == Facet.FacetType.CATEGORICAL) {
				Point p2 = new Point(p.x - components[i].getX(), p.y - components[i].getY());
				for (Component cmp : components[i].getComponents()) {
					if (cmp.getBounds().contains(p2)) {
						((TagLabel)cmp).toggleSelected();
						break;
					}
				}
				
				break;
			}
		}
		
		rootFrame.logger.logAction("infopanel-click");
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		this.requestFocus();
	}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent me) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}

	@Override
	public void dragGestureRecognized(DragGestureEvent e) {
		Point p = e.getDragOrigin();
		
		if (selector.getSelectedIndex() < 0)
			return;
			
		Publication pub = null;
		boolean isAll = true;
		if (selector.getSelectedIndex() < publications.size()) {
			pub = publications.get(selector.getSelectedIndex());
			isAll = false;
		}
		
		if (!isAddMultiple) {
			for (int i = 0; i < components.length; i++) {
				if (components[i].isVisible() && components[i].getBounds().contains(p)) {
					if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) {
						rootFrame.glassPane.setImage(PivotSlice.getScreenShot(components[i]));
						e.startDrag(DragSource.DefaultCopyDrop, createTransferable(i, isAll, pub, -1));
					}
					else {
						Point p2 = new Point(p.x - components[i].getX(), p.y - components[i].getY());
						Component[] cmp = components[i].getComponents();
						for (int j = 0; j < cmp.length && j < maxDisplayNum; j++) {
							if (cmp[j].getBounds().contains(p2)) {
								rootFrame.glassPane.setImage(PivotSlice.getScreenShot(cmp[j]));
								e.startDrag(DragSource.DefaultCopyDrop, createTransferable(i, isAll, pub, j));
								break;
							}
						}
					}
					
					break;
				}
			}
		}
		else {
			for (int i = 0; i < components.length; i++) {
				if (components[i].isVisible() && components[i].getBounds().contains(p)) {
					Constraint.TransferableConstraint trans = createTransferable(i, isAll, pub, -1);
					if (trans == null)
						return;
					rootFrame.glassPane.setImage(PivotSlice.getScreenShot(components[i]));
					e.startDrag(DragSource.DefaultCopyDrop, trans);
				}
			}
		}	
		
		rootFrame.logger.logAction("infopanel-drag");
	}
	
	@Override
	public void keyPressed(KeyEvent ke) {
		if (!isAddMultiple && ke.getKeyCode() == KeyEvent.VK_CONTROL)
			isAddMultiple = true;
	}

	@Override
	public void keyReleased(KeyEvent ke) {
		if (isAddMultiple && ke.getKeyCode() == KeyEvent.VK_CONTROL) {
			isAddMultiple = false;
			for (int i = 0; i < components.length; i++)  {
				if (Facet.availableFacets[i].facetType == Facet.FacetType.CATEGORICAL) {
					for (Component cmp : components[i].getComponents()) {
						((TagLabel)cmp).setSelected(true);
					}
				}
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent arg0) {}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods
	private void performAggregation() {		
		Object[] storage = new Object[Facet.availableFacets.length];
		for (int i = 0; i < Facet.availableFacets.length; i++) {
			if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) 
				storage[i] = new Point(Integer.MAX_VALUE, 0);
			else 
				storage[i] = new HashMap<Long, CategoricalAggr>();
		}
		
		// iterate through publications
		for (Publication pub : publications) {
			for (int i = 0; i < Facet.availableFacets.length; i++) {
				if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) {
					int val = Facet.getNumericalFacetValue(pub, i);
					Point range = (Point)storage[i];
					if (val < range.x)
						range.x = val;
					if (val > range.y)
						range.y = val;
				}
				else {
					HashMap<Long, CategoricalAggr> range = (HashMap<Long, CategoricalAggr>)storage[i];
					for (Long id : Facet.getCategoricalFacetValueIDs(pub, i)) {
						if (!range.containsKey(id))
							range.put(id, new CategoricalAggr(id));
						else
							range.get(id).count++;
					}
				}
			}
		}
		
		// assign values
		aggregations.clear();
		for (int i = 0; i < Facet.availableFacets.length; i++) {
			if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) 
				aggregations.add(new NumericalAggr(((Point)storage[i]).x, ((Point)storage[i]).y));
			else
				aggregations.add(new LinkedList<CategoricalAggr>(
						((HashMap<Long, CategoricalAggr>)storage[i]).values()
					));
		}
		
		// sorting
		for (int i = 0; i < Facet.availableFacets.length; i++) {
			if (Facet.availableFacets[i].facetType == Facet.FacetType.CATEGORICAL) {
				LinkedList<CategoricalAggr> agg = (LinkedList<CategoricalAggr>)aggregations.get(i);
				Collections.sort(agg, new Comparator<CategoricalAggr>(){
					@Override
					public int compare(CategoricalAggr arg0, CategoricalAggr arg1) {						
						return arg1.count - arg0.count;
					}} );
			}
		}
	}
	
	private Constraint.TransferableConstraint createTransferable(int facetID, boolean isAll, Publication pub, int index) {
		Constraint.TransferableConstraint trans = null;
		
		if (!isAll) {	// single selection
			if (Facet.getFacetType(facetID) == Facet.FacetType.CATEGORICAL) {
				if (index != -1) {
					trans = new Constraint.TransferableConstraint(facetID, Facet.getCategoricalFacetValueID(pub, facetID, index));
				}
				else {
					LinkedList<Long> ids = new LinkedList<Long>();
					
					Component[] cmps = components[facetID].getComponents();
					int i = 0;
					for (Long id : Facet.getCategoricalFacetValueIDs(pub, facetID)) {
						if (((TagLabel)cmps[i]).selected)
							ids.add(id);
						i++;
					}
					
					if (ids.size() != 0)
						trans = new Constraint.TransferableConstraint(facetID, ids);
					else
						rootFrame.showInfoMessage("No attributes selected.");
				}
			}
			else {
				int value = Facet.getNumericalFacetValue(pub, facetID);
				trans = new Constraint.TransferableConstraint(facetID, value, value);
			}
		}
		else {	// aggregation
			if (Facet.getFacetType(facetID) == Facet.FacetType.CATEGORICAL) {
				if (index != -1) {
					CategoricalAggr cagg = ((LinkedList<CategoricalAggr>)aggregations.get(facetID)).get(index);
					trans = new Constraint.TransferableConstraint(facetID, cagg.id);
				}
				else {
					LinkedList<Long> ids = new LinkedList<Long>();
					
					Component[] cmps = components[facetID].getComponents();
					int i = 0;
					for (CategoricalAggr cagg : (LinkedList<CategoricalAggr>)aggregations.get(facetID)) {		
						if (i < maxDisplayNum) {
							if (((TagLabel)cmps[i]).selected)
								ids.add(cagg.id);
						}
						else if (((TagLabel)cmps[maxDisplayNum]).selected) {
							ids.add(cagg.id);
						}
						else {
							break;
						}
						
						i++;
					}
					
					if (ids.size() != 0)
						trans = new Constraint.TransferableConstraint(facetID, ids);
				}
			}
			else {
				NumericalAggr nagg = (NumericalAggr)aggregations.get(facetID);
				trans = new Constraint.TransferableConstraint(facetID, nagg.from, nagg.to);
			}
		}
		
		return trans;
	}
	
	private void setPanelAttributes(final JPanel panel) {
		panel.setBorder(border);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	}
	/*
	private void setLabelAttributes(JLabel label, int facetID) {
		label.setOpaque(true);
		label.setBorder(border);
		label.setFont(font);
		if (facetID >= 0) {	
			label.setBackground(Facet.availableFacets[facetID].facetColor);
			label.setIcon(Facet.availableFacets[facetID].facetIcon);
		}
	}*/
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	private class SelectorListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
	        int index = selector.getSelectedIndex();
			
	        if (publications.size() == 0) {
	        	title.setText("");
	        	for (int i = 0; i < components.length; i++) {
	        		if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) {
	        			JLabel label = (JLabel)components[i];
	        			label.setText("");
	        		}
	        		else {
	        			JPanel panel = (JPanel)components[i];
	        			panel.removeAll();
	        		}
	        	}
	        }
	        else if (publications.size() != 0 && index >= 0) {		
		        if (index < publications.size()) { // single selection
		        	final Publication pub = publications.get(index);
		        	
        			if (pub.getFullVersionURL().size() > 0) {
        				title.setText("<html>" + "<a href=\"" + pub.getFullVersionURL().get(0) + "\">" 
        						+ pub.getTitle()  + "</a></html>");
        			}
        			else {
        				title.setText("<html>" + pub.getTitle() + "</html>");
        			}
		        	
		        	for (int i = 0; i < components.length; i++) {
		        		if (Facet.getFacetType(i) == Facet.FacetType.NUMERICAL) {
		        			JLabel label = (JLabel)components[i];
		        			label.setText("<html>" + Facet.getNumericalFacetString(pub, i) + "</html>");
		        			
		        		}
		        		else {
		        			JPanel panel = (JPanel)components[i];
		        			panel.removeAll();
		        			
		        			LinkedList<Long> tagIDs = Facet.getCategoricalFacetValueIDs(pub, i);
		        			
		        			if (tagIDs.size() == 0)
		        				panel.setVisible(false);
		        			else {
		        				panel.setVisible(true);
		        				
		        				LinkedList<Long> attrIDs = new LinkedList<Long>();
		        				Constraint cons = filterx.getConstraint(i);
		        				if(cons != null) 
		        					attrIDs.addAll(cons.getConstraintData().valueIDs);
		        				cons = filtery.getConstraint(i);
		        				if(cons != null) 
		        					attrIDs.addAll(cons.getConstraintData().valueIDs);
		        				
		        				for (Long id : tagIDs) {
		        					TagLabel label = new TagLabel("<html>" + Facet.getCategoricalFacetString(id, i) 
		        							+ "</html>", i);
		        					if (attrIDs.contains(id))
		        						label.setIncluded();
		        	
		        					panel.add(label);
		        				}
		        			}		
		        		}
		        	}
		        }
		        else {	// the all selection
		        	title.setText("<html>" + publications.size() + " publications</html>");
		        	for (int i = 0; i < components.length; i++) {
		        		if (Facet.availableFacets[i].facetType == Facet.FacetType.NUMERICAL) {
		        			JLabel label = (JLabel)components[i];
		        			NumericalAggr agg = (NumericalAggr)aggregations.get(i);
		        			label.setText("<html>" + agg.from + "---" + agg.to + "</html>");
		        			label.setIcon(Facet.availableFacets[i].facetIcon);
		        		}
		        		else {
		        			JPanel panel = (JPanel)components[i];
		        			panel.removeAll();
		        			
		        			LinkedList<CategoricalAggr> agg = (LinkedList<CategoricalAggr>)aggregations.get(i);
		        			if (agg.size() == 0)
		        				panel.setVisible(false);
		        			else {
		        				panel.setVisible(true);
		        				
		        				LinkedList<Long> attrIDs = new LinkedList<Long>();
		        				Constraint cons = filterx.getConstraint(i);
		        				if(cons != null) 
		        					attrIDs.addAll(cons.getConstraintData().valueIDs);
		        				cons = filtery.getConstraint(i);
		        				if(cons != null) 
		        					attrIDs.addAll(cons.getConstraintData().valueIDs);
		        				
		        				for (int j = 0; j < maxDisplayNum && j < agg.size(); j++) {
		        					CategoricalAggr cagg = agg.get(j);
		        					TagLabel label = new TagLabel("<html>" + Facet.getCategoricalFacetString(cagg.id, i) + " "
		        							+ cagg.count + "</html>", i);
		        					if (attrIDs.contains(cagg.id))
		        						label.setIncluded();
		        					
		        					panel.add(label);
		        				}
		        				
		        				if (agg.size() > maxDisplayNum) {
		        					TagLabel label = new TagLabel("<html>" + (agg.size() - maxDisplayNum) + " more..." + "</html>", i);
		        					panel.add(label);
		        				}
		        			}
		        		}
		        	}
		        }
		        
	        } 
	        
	        SwingUtilities.invokeLater(new Runnable(){
				@Override
				public void run() {
					int height = 0;
			        for(Component cmp : InfoPanel.this.getComponents()) {
			        	height += cmp.getPreferredSize().height;
			        }
			        InfoPanel.this.setPreferredSize(new Dimension(160, height + 100));
			        InfoPanel.this.revalidate();
				}
	        	
	        });
	       
		}
		
		
	}
	
	private class TagLabel extends JLabel {
		public boolean selected = true;
		public boolean included = false;
		public int facetID;
		
		public TagLabel(String text, int fid) {
			super(text);
			facetID = fid;
			this.setOpaque(true);
			this.setBorder(border);
			this.setFont(font);
			this.setIcon(Facet.availableFacets[facetID].facetIcon);
			this.setBackground(Facet.availableFacets[facetID].facetColor);
		}
		
		public void setSelected(boolean val) {
			selected = val;
			if (!selected)
				this.setBackground(Color.lightGray);
			else if (!included)
				this.setBackground(Facet.availableFacets[facetID].facetColor);
			else
				this.setBackground(Facet.availableFacets[facetID].facetBrightColor);
		}
		
		public void toggleSelected() {
			selected = !selected;
			if (!selected)
				this.setBackground(Color.lightGray);
			else if (!included)
				this.setBackground(Facet.availableFacets[facetID].facetColor);
			else
				this.setBackground(Facet.availableFacets[facetID].facetBrightColor);
		}
		
		public void setIncluded() {
			included = true;
			this.setBackground(Facet.availableFacets[facetID].facetBrightColor);
		}
	}
	
	private class CategoricalAggr {
		public Long id;
		public int count = 0;
		public CategoricalAggr(Long id) {
			this.id = id;
			count = 1;
		}
	}
	
	private class NumericalAggr {
		public int from = -1;
		public int to;
		public NumericalAggr(int from, int to) {
			this.from = from;
			this.to = to;
		}
	}

}
