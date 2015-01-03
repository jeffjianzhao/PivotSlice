package pivotslice;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;


import org.datacontract.schemas._2004._07.libra_service.ReferenceRelationship;

import pivotslice.GraphCell.NodeVisual;

import com.microsoft.research.PagedList;
import com.microsoft.research.Publication;
import com.microsoft.research.query.AcademicSearchQuery;

public class OperationPanel extends JPanel {

	private final PivotSlice rootFrame;
	
	private JButton refButton = new JButton(new ImageIcon(PivotSlice.class.getResource("/images/ref-icon.png")));
	private JButton citeButton = new JButton(new ImageIcon(PivotSlice.class.getResource("/images/cite-icon.png")));
	private JButton cutButton = new JButton(new ImageIcon(PivotSlice.class.getResource("/images/cut-icon.png")));
	private JButton layoutButton = new JButton(new ImageIcon(PivotSlice.class.getResource("/images/layout-icon.png")));
	
	private JToggleButton viewButton = new JToggleButton(new ImageIcon(PivotSlice.class.getResource("/images/square-icon.png")));
	private JToggleButton xAggrButton = new JToggleButton(new ImageIcon(PivotSlice.class.getResource("/images/resize-icon2.png")));
	private JToggleButton yAggrButton = new JToggleButton(new ImageIcon(PivotSlice.class.getResource("/images/resize-icon1.png")));
	
	private String[] comboStr = new String[]{"no links", "internal links", "crossing links", "all links"};
	private JComboBox edgeComboBox = new JComboBox(comboStr);
	
	private JProgressBar progressBar = new JProgressBar(0, 100);
	
	public OperationPanel(PivotSlice frame) {
		rootFrame = frame;
		
		JPanel north = createButtonUIPanel();
		
		progressBar.setStringPainted(true);
		progressBar.setPreferredSize(new Dimension(300, 15));
		
		JPanel south = new JPanel(new FlowLayout(FlowLayout.LEADING));
		south.add(new JLabel("Operation Progress"));
		south.add(progressBar);
		
		this.setLayout(new BorderLayout());
		this.add(north, BorderLayout.NORTH);
		this.add(south, BorderLayout.SOUTH);
	}
	
	private JPanel createButtonUIPanel() {
		Dimension dim = new Dimension(40, 25);
		
		refButton.setPreferredSize(dim);
		refButton.setToolTipText("Bring References");
		refButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int result = rootFrame.showConfirmMessage("This may take a few minutes. Do you want to continue?");
				if (result == JOptionPane.OK_OPTION) {
					searchPublications(false);
					
					rootFrame.logger.logAction("oppanel-search references");
				}
			}
			
		});
		
		citeButton.setPreferredSize(dim);
		citeButton.setToolTipText("Bring Citations");
		citeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int result = rootFrame.showConfirmMessage("This may take a few minutes. Do you want to continue?");
				if (result == JOptionPane.OK_OPTION) {
					searchPublications(true);
					
					rootFrame.logger.logAction("oppanel-search citations");
				}
			}
			
		});
		
		cutButton.setPreferredSize(dim);
		cutButton.setToolTipText("Prune Data");
		cutButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int result = rootFrame.showConfirmMessage("Are you sure to prune the dataset?");
				if (result == JOptionPane.OK_OPTION) {
					pruneData();
					
					rootFrame.logger.logAction("oppanel-prune data");
				}	
			}
			
		});
		
		layoutButton.setPreferredSize(dim);
		layoutButton.setToolTipText("Re-Layout");
		layoutButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				rootFrame.graphCanvas.reLayoutNodeVisuals();
				
				rootFrame.logger.logAction("oppanel-relayout");
			}
		});
		
		/*
		edgeButton.setPreferredSize(dim);
		edgeButton.setToolTipText("Toggle show links");
		edgeButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				rootFrame.graphCanvas.setShowLinks(edgeButton.isSelected());
				
				rootFrame.logger.logAction("oppanel-edge");
			}
		});
		*/
		
		viewButton.setPreferredSize(dim);
		viewButton.setToolTipText("Toggle Matrix View");
		viewButton.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				rootFrame.graphCanvas.setMatrixView(viewButton.isSelected());		
				
				rootFrame.logger.logAction("oppanel-matrix view");
			}	
		});
		
		xAggrButton.setPreferredSize(dim);
		xAggrButton.setToolTipText("Toggle Aggregation by X-axis");
		xAggrButton.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				rootFrame.graphCanvas.setCollapsedView(xAggrButton.isSelected(), true);
				
				rootFrame.logger.logAction("oppanel-collapse x");
			}	
		});
		
		yAggrButton.setPreferredSize(dim);
		yAggrButton.setToolTipText("Toggle Aggregation by Y-axis");
		yAggrButton.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				rootFrame.graphCanvas.setCollapsedView(yAggrButton.isSelected(), false);
				
				rootFrame.logger.logAction("oppanel-collapse y");
			}	
		});
		
		edgeComboBox.setPreferredSize(new Dimension(80, 25));
		edgeComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				String event = (String)edgeComboBox.getSelectedItem();
				if (event.equals("no links")) 
					rootFrame.graphCanvas.setShowLinks(false, false);
				else if (event.equals("crossing links")) 
					rootFrame.graphCanvas.setShowLinks(false, true);
				else if (event.equals("internal links")) 
					rootFrame.graphCanvas.setShowLinks(true, false);
				else if (event.equals("all links")) 
					rootFrame.graphCanvas.setShowLinks(true, true);
			}
			
		});
		
		JPanel north = new JPanel(new FlowLayout(FlowLayout.LEADING));
		north.add(citeButton);
		north.add(refButton);
		north.add(cutButton);
		north.add(viewButton);
		north.add(xAggrButton);
		north.add(yAggrButton);
		north.add(layoutButton);
		north.add(edgeComboBox);
		
		return north;
	}
	
	public void searchPublications(boolean isCitation) {
		Set<Publication> pubs = rootFrame.graphCanvas.getSelectedPublications();
		if (pubs == null || pubs.isEmpty()) {
			rootFrame.showInfoMessage("No publication is selected.");
			return;
		}
		
		final MSSearchTask task = new MSSearchTask(pubs, isCitation);
		task.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if("progress".equals(evt.getPropertyName())) {
					rootFrame.opPanel.setProgressValue(task.getProgress());
				}						
			}
		});
		task.execute();	
	}
	
	public void updateButtonStates() {
		GraphCell cell = rootFrame.graphCanvas.getSelectedCell();
		if (cell != null) {
			xAggrButton.setSelected(cell.xAggr);
			yAggrButton.setSelected(cell.yAggr);
			viewButton.setSelected(cell.matrixLayout);
		}
	}
	
	public void setProgressValue(int val) {
		progressBar.setValue(val);
	}
	
	public void pruneData() {
		GraphCell cell = rootFrame.graphCanvas.getSelectedCell();
		
		if (cell == null && rootFrame.graphCanvas.getSelectedNodes().isEmpty()) {
			rootFrame.showInfoMessage("No cell or node is selected.");
			return;
		}
		
		try {
			PivotSlice.dataSource.saveNetwork(new File("autosave.dat"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// data source
		if (rootFrame.graphCanvas.getSelectedNodes().isEmpty()) 
			PivotSlice.dataSource.pruneNetwork(cell.publications);	
		else 
			PivotSlice.dataSource.pruneNetwork(rootFrame.graphCanvas.getSelectedPublications());
		
		// GUI
		rootFrame.graphCanvas.initialized = false;
		rootFrame.facetBrowserX.initNodesFilter();					
		rootFrame.facetBrowserY.initNodesFilter();
		rootFrame.graphCanvas.initGraphCanvas();
	
		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() {	
				rootFrame.historyPanel.addState();	
			}});
		
		//if (rootFrame.graphCanvas.getSelectedNode() != null) {
		//	rootFrame.graphCanvas.setSelectedPublication(rootFrame.graphCanvas.getSelectedNode().pubs.get(0).getID());
		//}
	}
	
	public class MSSearchTask extends SwingWorker<Void, Void> implements DataSource.ProgressTask {
		
		private Set<Publication> publication;
		private boolean isCitation;
		
		public MSSearchTask(Set<Publication> pubs, boolean citation) {
			publication = pubs;
			isCitation = citation;
		}
		
		@Override
		public void advanceProgress(int progress) {
			this.setProgress(progress);
		}

		
        @Override
        public void done() {
        	this.setProgress(100);
        	PivotSlice.dataSource.updateAttributeMaps();
        	rootFrame.graphCanvas.reDistributeNodesToAllGraphCells();
			citeButton.setEnabled(true);
			refButton.setEnabled(true);
        }

		@Override
		protected Void doInBackground() throws Exception {
			citeButton.setEnabled(false);
			refButton.setEnabled(false);
			
			List<Publication> response = new LinkedList<Publication>();
			
			try {
				this.setProgress(1);
				for (Publication pub : publication) {
					AcademicSearchQuery<Publication> query = null;
					if (isCitation) {
						query = PivotSlice.dataSource.getNewQuery().withPublicationId(pub.getID().intValue())
							.withReferenceRelationship(ReferenceRelationship.CITATION)
							.withStartIndex(1).withEndIndex(100);
					}
					else {
						query = PivotSlice.dataSource.getNewQuery().withPublicationId(pub.getID().intValue())
							.withReferenceRelationship(ReferenceRelationship.REFERENCE)
							.withStartIndex(1).withEndIndex(100);
					}
					
					response.addAll(PivotSlice.dataSource.executeQuery(query));
					this.setProgress(this.getProgress() + 98 / publication.size());
				}
	
				this.setProgress(1);
				if (response.size() > 0)
					PivotSlice.dataSource.addToNetwork(response, this);
			}
			catch (Exception e) {
				e.printStackTrace();
				rootFrame.showErrorMessage("Search error:\n" + e.toString());
			}
				
			return null;
		}
	}
}
