package pivotslice;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingWorker;

import com.microsoft.research.Author;
import com.microsoft.research.PagedList;
import com.microsoft.research.Publication;
import com.microsoft.research.query.AcademicSearchQuery;

public class SearchDialog extends JDialog {
	
	private static final int maxResultNum = 50;
	private static final Font font = new Font("SansSerif", Font.PLAIN, 9);
	private static final Color selectionColor = new Color(173, 216, 230);

	private DefaultListModel listModel = new DefaultListModel();
	private JList resultList;
	private JButton addButton = new JButton("Add Selected");
	private JButton cancelButton = new JButton("Cancel");
	private JLabel infoLabel = new JLabel();
	
	private final PivotSlice rootFrame;
	
	public SearchDialog(PivotSlice frame, String text) {
		super(frame, "Search Results", true);
		
		this.setSize(new Dimension(800, 400));
		this.setResizable(false);
		this.setLocationRelativeTo(frame);
		this.setLayout(new BorderLayout());
		
		rootFrame = frame;
		
		resultList = new JList(listModel);
		resultList.setCellRenderer(new ResultListCellRenderer());
		
		addButton.setPreferredSize(new Dimension(120, 24));
		addButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if (resultList.getSelectedIndices().length > 0)
					addPublications();
				
				rootFrame.logger.logAction("searchdialog-add publications");
			}
			
		});
		
		cancelButton.setPreferredSize(new Dimension(120, 24));
		cancelButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				SearchDialog.this.dispose();
			}
			
		});
		
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		panel.add(infoLabel);
		panel.add(addButton);
		panel.add(cancelButton);
		
		this.getContentPane().add(new JScrollPane(resultList), BorderLayout.CENTER);
		this.getContentPane().add(panel, BorderLayout.SOUTH);
		
		conductSearch(text);
	}
	
	private void conductSearch(String text) {
		final SearchTask task = new SearchTask(text);
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
	
	private void addPublications() {
		List<Publication> pubs = new LinkedList<Publication>();
		for (Object ob : resultList.getSelectedValues()) {
			pubs.add((Publication)ob);
		}
		
		if (pubs.size() > 0) {
			final AdditionTask task = new AdditionTask(pubs);
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
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	private class ResultListCellRenderer extends JLabel implements ListCellRenderer {

		public ResultListCellRenderer() {
			this.setFont(font);
			this.setOpaque(true);
			//this.setPreferredSize(new Dimension(360, 50));
		}
		
		private String getDisplayString(Publication pub) {
			StringBuilder sb = new StringBuilder("<html>");
			sb.append(pub.getTitle() + ". <b>");
			
			for (Author au : pub.getAuthor()) {
				sb.append(au.getFirstName().charAt(0) + ". " + au.getLastName() + ", ");
			}
			//sb.replace(sb.length() - 2, -1, ".</b> ");
			sb.append("</b>");
			
			if (pub.getConference() != null)
				sb.append("<i>" + pub.getConference().getFullName() + "</i>. ");
			else if (pub.getJournal() != null)
				sb.append("<i>" + pub.getJournal().getFullName() + "</i>. ");
			
			sb.append(pub.getYear());
			sb.append(".</html>");
			
			return sb.toString();
		}
		
		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			if (value instanceof Publication) {
				Publication pub = (Publication)value;
				this.setText(getDisplayString(pub));
				
				if (isSelected)
					this.setBackground(selectionColor);
				else
					this.setBackground(Color.white);
			}
			
			return this;
		}
		
	}
	
	private class SearchTask extends SwingWorker<Void, Publication> {

		private String queryText;
		private PagedList<Publication> response;
		
		public SearchTask(String text) {
			queryText = text;
		}
		
		@Override
        public void done() {
			for (Publication pub : response)
				listModel.addElement(pub);
			
			this.setProgress(100);
			addButton.setEnabled(true);
			infoLabel.setText(String.format("%d publications found", response.size()));
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			this.setProgress(30);
			addButton.setEnabled(false);
			
			AcademicSearchQuery<Publication> query = PivotSlice.dataSource.getNewQuery().withFullTextQuery(queryText)
					.withStartIndex(1).withEndIndex(maxResultNum);
			
			try {
				response = PivotSlice.dataSource.executeQuery(query);
			}
			catch (Exception e) {
				e.printStackTrace();
				rootFrame.showErrorMessage("Search error:\n" + e.toString());
			}

			return null;
		}
		
	}
	
	private class AdditionTask extends SwingWorker<Void, Void> implements DataSource.ProgressTask {

		private List<Publication> publications;
		
		public AdditionTask(List<Publication> pubs) {
			publications = pubs;
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			this.setProgress(1);
			PivotSlice.dataSource.addToNetwork(publications, this);
			return null;
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
        	SearchDialog.this.dispose();
        }
		
	}
}
