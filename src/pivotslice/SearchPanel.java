package pivotslice;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


import com.microsoft.research.Author;
import com.microsoft.research.Conference;
import com.microsoft.research.Journal;
import com.microsoft.research.Keyword;
import com.microsoft.research.Publication;

public class SearchPanel extends JPanel implements DragGestureListener {
	
	private static final int pauseTime = 1000;
	private static final int itemSize = 15;
	private static final float itemAlpha = 0.8f;
	private static final int maxItemSize = 30;
	private static final Border itemBorder = BorderFactory.createLineBorder(Color.BLACK);
	
	private final String[] facetSearchTags;
	private JTextField searchBox = new JTextField();
	private JButton addButton = new JButton(new ImageIcon(PivotSlice.class.getResource("/images/add-icon.png")));
	private DefaultListModel listModel = new DefaultListModel();
	private Popup popupPanel;
	private JList popupList;
	private Timer timer;
	private SearchTask task;
	private boolean itemSelected = false;
	private boolean dragRecogonized = false;
	private SearchItem filterItem;
	
	private final PivotSlice rootFrame;
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public SearchPanel(PivotSlice frame) {
		rootFrame = frame;
		
		facetSearchTags = new String[Facet.availableFacets.length];
		for (int i = 0; i < Facet.availableFacets.length; i++) 
			facetSearchTags[i] = "/" + Facet.availableFacets[i].facetName.toLowerCase();
		
		timer = new Timer(pauseTime, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				processSearchText();
			}	
		});
		timer.setRepeats(false);
		
		ListMouseListener listener = new ListMouseListener();
		popupList = new JList(listModel);
		popupList.addMouseListener(listener);
		popupList.addMouseMotionListener(listener);
		popupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		popupList.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		popupList.setCellRenderer(new FacetListCellRenderer());		
		
		createUI();
		this.setLayout(new FlowLayout(FlowLayout.LEFT));
		this.setPreferredSize(new Dimension(450, 30));	
	}
	
	@Override
	public void dragGestureRecognized(DragGestureEvent e) {
		dragRecogonized = true;
		if (e.getDragAction() == DnDConstants.ACTION_COPY) {
			if (timer.isRunning())
				timer.stop();
			
			String text = searchBox.getText().toLowerCase();
			if (text.startsWith("/")) {
				for (int i = 0; i < facetSearchTags.length; i++) {
					if (text.startsWith(facetSearchTags[i])) {
						Constraint.ConstraintData cdata = generateConstraintData(i, text);
						if (cdata == null)
							return;
						
						rootFrame.glassPane.setImage(PivotSlice.getScreenShot(searchBox));
						e.startDrag(DragSource.DefaultCopyDrop, new Constraint.TransferableConstraint(cdata));
						
						rootFrame.logger.logAction("searchpanel-drag attribute");
						break;
					}
				}
			}
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods
	private void createUI() {
		searchBox.setPreferredSize(new Dimension(400, 20));
		searchBox.getDocument().addDocumentListener(new DocumentListener(){
			@Override
			public void changedUpdate(DocumentEvent arg0) {}
			@Override
			public void insertUpdate(DocumentEvent e) {
				timer.restart();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				timer.restart();
			}			
		});
		/*
		searchBox.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent arg0) {}
			@Override
			public void keyReleased(KeyEvent arg0) {
				if (arg0.getKeyCode() == KeyEvent.VK_ENTER) {
					addFilterConstraint(null);
				}
			}
			@Override
			public void keyTyped(KeyEvent arg0) {}			
		});
		*/
		searchBox.addFocusListener(new FocusListener(){
			@Override
			public void focusGained(FocusEvent arg0) {}
			@Override
			public void focusLost(FocusEvent arg0) {
				if (timer.isRunning())
					timer.stop();
				if (task != null) 
					task.cancel(true);
				if (popupPanel != null)
					popupPanel.hide();
			}			
		});
		
		addButton.setPreferredSize(new Dimension(40, 25));
		addButton.setToolTipText("Add Items");
		addButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!dragRecogonized)
					addFilterConstraint();
				dragRecogonized = false;
			}
		});
		
		DragSource ds = new DragSource();
		ds.createDefaultDragGestureRecognizer(addButton, DnDConstants.ACTION_COPY, this);
		ds.addDragSourceMotionListener(rootFrame.dragListener);
		ds.addDragSourceListener(rootFrame.dragListener);
		
		this.add(searchBox);
		this.add(addButton);
	}

	private void processSearchText() {
		//cancel previous task, start a new search task
		if (task != null) 
			task.cancel(true);
		if (itemSelected) {
			itemSelected = false;
			return;
		}
		
		filterItem = null;
		listModel.removeAllElements();
		searchBox.setBackground(Color.WHITE);
		if (popupPanel != null) 
			popupPanel.hide();
		popupPanel = PopupFactory.getSharedInstance().getPopup(searchBox, popupList, 
			searchBox.getLocationOnScreen().x, searchBox.getLocationOnScreen().y + searchBox.getHeight());
		
		String text = searchBox.getText().toLowerCase();
		if (text.startsWith("/")) {
			for (int i = 0; i < facetSearchTags.length; i++) {
				if (text.startsWith(facetSearchTags[i])) {
					searchBox.setBackground(Facet.availableFacets[i].facetColor);
					
					if (Facet.getFacetType(i) == Facet.FacetType.CATEGORICAL) {
						// categorical facets, start a search task
						String searchText = text.substring(facetSearchTags[i].length()).trim();
						if (!searchText.isEmpty()) {
							task = new SearchTask(searchText, i);
							task.execute();
						}	
					}
					
					break;
				}
			}
		}
		else if (!text.isEmpty()) {
			// title search or all-item search
			task = new SearchTask(text, -1);
			task.execute();
		}
	}
	
	private void addFilterConstraint() {
		if (timer.isRunning())
			timer.stop();
		
		String text = searchBox.getText().toLowerCase();
		if (text.startsWith("/")) {		// already found, add the contraint
			for (int i = 0; i < facetSearchTags.length; i++) {
				if (text.startsWith(facetSearchTags[i])) {
					Constraint.ConstraintData cdata = generateConstraintData(i, text);
					if (cdata == null)
						return;
					
					GraphCell cell = rootFrame.graphCanvas.getSelectedCell();
					boolean useX = rootFrame.facetBrowserX.getNodesFilters().size() <= 
						rootFrame.facetBrowserY.getNodesFilters().size();
					if (cell == null && useX) 
						rootFrame.facetBrowserX.addConstraint(0, cdata);
					else if (cell == null && !useX)
						rootFrame.facetBrowserY.addConstraint(0, cdata);
					else if (useX)
						rootFrame.facetBrowserX.addConstraint(cell.gridx, cdata);
					else
						rootFrame.facetBrowserY.addConstraint(cell.gridy, cdata);
					
					rootFrame.historyPanel.addOperation(cdata);
					
					rootFrame.logger.logAction("searchpanel-add attribute");
					break;
				}
			}
		}
		else if (!text.trim().isEmpty()) {	// search content online
			int result = rootFrame.showConfirmMessage("Do you want to conduct an online search for \'" 
					+ searchBox.getText() + "\'? This may take a few minutes.");
			if (result == JOptionPane.OK_OPTION) {
				SearchDialog dialog = new SearchDialog(rootFrame, searchBox.getText().trim());
				dialog.setVisible(true);
				
				rootFrame.logger.logAction("searchpanel-search online");
			}
		}
		
	}
	
	private Constraint.ConstraintData generateConstraintData(int facetID, String text) {
		Constraint.ConstraintData cdata = null;
		if (filterItem  == null) {	// numerical constraint
			String numText = text.substring(facetSearchTags[facetID].length()).trim();
			String[] nums = numText.split("-");
			try {
				if (nums.length == 1) {
					int val = Integer.parseInt(nums[0]);
					cdata = new Constraint.ConstraintData(facetID);
					cdata.fromValue = cdata.toValue = val;
				}
				else if (nums.length == 2) {
					int val1 = Integer.parseInt(nums[0]);
					int val2 = Integer.parseInt(nums[1]);
					if (val1 > val2)
						return null;
					cdata = new Constraint.ConstraintData(facetID);
					cdata.fromValue = val1;
					cdata.toValue = val2;
				}
				else
					return null;
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		else {	// categorical constraint
			cdata = new Constraint.ConstraintData(filterItem.facetID);
			cdata.valueIDs.add(filterItem.valueID);
		}
		
		return cdata;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	private class SearchItem {
		public int facetID;
		public Long valueID;
		public String text;
		
		public SearchItem(String str, int fid, Long val) {
			text = str;
			facetID = fid;
			valueID = val;
		}
	}

	private class SearchTask extends SwingWorker<Void, SearchItem> {
		private String queryText;
		private int facetID;
		
		public SearchTask(String text, int fid) {
			facetID = fid;
			queryText = text;
		}
		
		private void searchAttributes(int fid) {
			HashMap<Long, Object> hashmap = PivotSlice.dataSource.getAttributeMaps(fid);
			for(Object ob : hashmap.values()) {
				String content = null;
				Long id = null;
				switch (fid) {
					case 0:
						Author au = (Author)ob;
						id = au.getID();
						content = au.getFirstName() + " " + au.getLastName();
						break;
					case 1:
						id = ((Journal)ob).getID();
						content = ((Journal)ob).getFullName();
						break;
					case 2:
						id = ((Conference)ob).getID();
						content = ((Conference)ob).getFullName();
						break;
					case 4:
						id = ((Keyword)ob).getID();
						content = ((Keyword)ob).getName();
						break;
				}
				
				if (content.toLowerCase().contains(queryText)) {
					this.publish(new SearchItem(content, fid, id));
				}
			}
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			if (facetID != -1) {
				searchAttributes(facetID);
			}
			else {
				int[] fidArray = new int[] {0, 1, 2, 4};
				for (int id : fidArray)
					searchAttributes(id);
				for (Publication pub : PivotSlice.dataSource.getNetwork().graphNodes.values()) {
					if (pub.getTitle().toLowerCase().contains(queryText))
						this.publish(new SearchItem(pub.getTitle(), -1, pub.getID()));
				}
			}
			
			return null;
		}
		
		@Override
		protected void process(List<SearchItem> items) {			
			for (int i = listModel.size(); i < items.size() && i <= maxItemSize; i++)
				listModel.addElement(items.get(i));
			if (listModel.size() > maxItemSize) {
				listModel.addElement(new SearchItem("<more results... be more specific>", -1, -1L));
				this.cancel(true);
			}
			popupPanel.show();
		}
	}
	
	private class ListMouseListener extends MouseAdapter {
		@Override
		public void mouseMoved(MouseEvent me) {
			int index = popupList.locationToIndex(me.getPoint());
			popupList.setSelectedIndex(index);
		}
		
		@Override
		public void mouseClicked(MouseEvent me) {
			popupPanel.hide();
			itemSelected = true;
			filterItem = (SearchItem)popupList.getSelectedValue();
			if (filterItem != null) {
				if (filterItem.facetID != -1) {
					searchBox.setBackground(Facet.availableFacets[filterItem.facetID].facetColor);
					searchBox.setText(facetSearchTags[filterItem.facetID] + " " + filterItem.text);
				}
				else if (filterItem.valueID != -1) {
					searchBox.setText(filterItem.text);
					rootFrame.graphCanvas.setPublicationSelected(filterItem.valueID);
				}
				
				rootFrame.logger.logAction("searchpanel-click attribute");
			}
		}
	}
	
	private class FacetListCellRenderer extends JLabel implements ListCellRenderer {
		
		public FacetListCellRenderer() {
			this.setOpaque(true);
			this.setVerticalAlignment(CENTER);
			this.setPreferredSize(new Dimension(398, itemSize));
		}
		
		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			if (value instanceof SearchItem) {
				SearchItem item = (SearchItem)value;
				
				if (item.facetID >= 0) {
					float[] rgb = Facet.availableFacets[item.facetID].facetColor.getColorComponents(null);
					this.setBackground(new Color(rgb[0], rgb[1], rgb[2], itemAlpha));
					this.setIcon(Facet.availableFacets[item.facetID].facetIcon);
				}
				else {
					this.setBackground(Color.WHITE);
					this.setIcon(null);
				}
				
				this.setText(item.text);
				
				if (isSelected) 
					this.setBorder(itemBorder);
				else
					this.setBorder(null);
			}
			else if (value instanceof String) {
				this.setBackground(Color.WHITE);
				this.setIcon(null);
				this.setText((String)value);
			}
			
			return this;
		}
	}
	
	
}
