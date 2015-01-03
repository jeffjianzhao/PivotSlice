package pivotslice;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import pivotslice.Constraint.ConstraintData;


public class HistoryPanel extends JPanel implements MouseMotionListener, MouseListener, 
	DragGestureListener, ComponentListener {
	
	private static final int stateImageSize = 60;
	private static final int margin = 1;
	private static final int labelSize = 12;
	private static final Font font = new Font("SansSerif", Font.BOLD, 9);
	private static final Border border = BorderFactory.createLineBorder(Color.BLACK);
	private static final int maxOperationSize = 30;
	
	private static int maxStateSize = 5;
	
	private JPanel statePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
	private ArrayList<HistoryState> states = new ArrayList<HistoryState>();
	private int currentIndex = -1;
	private int hoveredIndex = -1;
	private JPanel operationPanel = new JPanel();
	private ArrayList<Constraint.ConstraintData> constraints = new ArrayList<Constraint.ConstraintData>();
	
	private final PivotSlice rootFrame;
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// public methods
	public HistoryPanel(PivotSlice frame) {
		rootFrame = frame;
		
		this.setBorder(BorderFactory.createTitledBorder("History"));
		
		this.setLayout(new BorderLayout());
		this.setPreferredSize(new Dimension(400, stateImageSize + margin * 2 + labelSize + 20));
		
		statePanel.addMouseListener(this);
		statePanel.addMouseMotionListener(this);	
		statePanel.setBorder(BorderFactory.createEtchedBorder());
		
		operationPanel.setPreferredSize(new Dimension(180, 10));
		operationPanel.setLayout(new GridBagLayout());
		operationPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		
		
		JScrollPane pane = new JScrollPane(operationPanel);
		pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pane.setPreferredSize(new Dimension(180, 10));
		
//		GridBagConstraints c = new GridBagConstraints();
//		c.gridx = 0;
//		c.weightx = 1;
//		c.weighty = 1;
//		c.fill = GridBagConstraints.HORIZONTAL;
//		c.anchor = GridBagConstraints.PAGE_START;
//		operationPanel.add(new JPanel(), c);
		
		DragSource ds = new DragSource();
		ds.createDefaultDragGestureRecognizer(operationPanel, DnDConstants.ACTION_COPY, this);
		ds.addDragSourceMotionListener(rootFrame.dragListener);
		ds.addDragSourceListener(rootFrame.dragListener);
		
		this.add(statePanel, BorderLayout.CENTER);
		this.add(pane, BorderLayout.EAST);
		
		this.addComponentListener(this);
	}
	
	public void addState() {
		HistoryState newState = new HistoryState();
		
		if (currentIndex == states.size() - 1) {
			if (states.size() >= maxStateSize) {
				HistoryState oldState = states.remove(0);
				statePanel.remove(oldState);
				currentIndex--;
			}
			
			states.add(newState);
			statePanel.add(newState);
			currentIndex++;
		}
		else {
			int count = states.size() - currentIndex - 1;
			for (int i = 0; i < count; i++) {
				HistoryState oldState = states.remove(currentIndex + 1);
				statePanel.remove(oldState);
			}	
			
			states.add(newState);
			statePanel.add(newState);
			currentIndex++;
		}	
		
		statePanel.validate();
		statePanel.repaint();
	}
	
	public void addOperation(Constraint.ConstraintData cdata) {
		Constraint.ConstraintData newdata = new Constraint.ConstraintData(cdata);
		
		for (Constraint.ConstraintData olddata : constraints) {
			if (olddata.equals(newdata))
				return;
		}
		
		if (constraints.size() >= maxOperationSize) {
			operationPanel.remove(constraints.size() - 1);
			constraints.remove(constraints.size() - 1);
		}
		
		constraints.add(0, newdata);
		
		JLabel label = createLabel(newdata);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.PAGE_START;
		operationPanel.add(label, c, 0);
		
		operationPanel.validate();
		operationPanel.repaint();
		
		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() {
				int height = 0;
		        for(Component cmp : operationPanel.getComponents()) {
		        	height += cmp.getPreferredSize().height;
		        }
		        operationPanel.setPreferredSize(new Dimension(160, height));
		        operationPanel.revalidate();
			}	
	     });
		
		//operationPanel.validate();
		//operationPanel.repaint();
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// private methods
	private static JLabel createLabel(Constraint.ConstraintData cdata) {
		JLabel label = new JLabel();
		
		label.setOpaque(true);
		label.setBorder(border);
		label.setFont(font);
		if (cdata.facetID >= 0) {	
			label.setBackground(Facet.availableFacets[cdata.facetID].facetColor);
			label.setIcon(Facet.availableFacets[cdata.facetID].facetIcon);
		}
		
		if (Facet.getFacetType(cdata.facetID) == Facet.FacetType.NUMERICAL) {
			if (cdata.fromValue == cdata.toValue)
				label.setText("<html>" + cdata.fromValue + "</html>");
			else
				label.setText("<html>" + cdata.fromValue + "---" + cdata.toValue + "</html>");
		}
		else {
			label.setText("<html>" + cdata.getValueStrings().get(0) + "</html>");
		}
		
		return label;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// event handler
	@Override
	public void componentHidden(ComponentEvent e) {}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentResized(ComponentEvent e) {
		int num = (this.getWidth() - 220) / stateImageSize;
		if (num < states.size()) {
			for (int i = 0; i < states.size() - num; i++) {
				HistoryState oldState = states.remove(0);
				statePanel.remove(oldState);
				currentIndex--;
			}
		}
		
		maxStateSize = num;
	}

	@Override
	public void componentShown(ComponentEvent e) {}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		Point p = e.getPoint();
		int current = -1;
		for (int i = 0; i < states.size(); i++) {
			if (states.get(i).getBounds().contains(p)) {
				current = i;
				break;
			}
		}
		
		if (current != -1 && current != currentIndex) {
			currentIndex = current;
			try {
				states.get(currentIndex).getState();
			} catch (Exception exp) {
				exp.printStackTrace();
				rootFrame.showErrorMessage("Retrieving states error:\n" + exp.toString());
			}
			statePanel.repaint();
		}
		
		rootFrame.logger.logAction("historypanel-click state");
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void mouseDragged(MouseEvent arg0) {}

	@Override
	public void mouseMoved(MouseEvent e) {
		Point p = e.getPoint();
		int hovered = -1;
		for (int i = 0; i < states.size(); i++) {
			if (states.get(i).getBounds().contains(p)) {
				hovered = i;
				break;
			}
		}
		
		if (hoveredIndex != hovered) {
			hoveredIndex = hovered;
			statePanel.repaint();
		}
	}
	
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		if (dge.getDragAction() == DnDConstants.ACTION_COPY) {
			Point p = dge.getDragOrigin();
			
			for (int i = 0; i < constraints.size(); i++) {
				Component cmp = operationPanel.getComponent(i);
				if (cmp.getBounds().contains(p)) {
					rootFrame.glassPane.setImage(PivotSlice.getScreenShot(cmp));
					dge.startDrag(DragSource.DefaultCopyDrop, new Constraint.TransferableConstraint(constraints.get(i)));
					
					rootFrame.logger.logAction("historypanel-drag attribute");
					break;
				}
			}
		}
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	public class HistoryState extends JPanel {
		
		public String timeString;
		public BufferedImage stateImage;
		
		private byte[] storage;
		
		public HistoryState() {	
			try {
				putState();
			} catch (IOException e) {
				e.printStackTrace();
				rootFrame.showErrorMessage("Storing states error:\n" + e.toString());
			}
			
			timeString = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
			BufferedImage image = PivotSlice.getScreenShot(rootFrame.centerPanel);
			stateImage = new BufferedImage(stateImageSize, stateImageSize, image.getType());
			Graphics2D g2 = stateImage.createGraphics();
			g2.drawImage(image, 0, 0, stateImageSize, stateImageSize, null);
			
			this.setPreferredSize(new Dimension(stateImageSize + margin * 2, stateImageSize + margin * 2 + labelSize));
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			Graphics2D g2 = (Graphics2D) g.create();
			
			int index = states.indexOf(this);
			if (index > currentIndex)
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
			else
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
			
			g2.drawImage(stateImage, margin, margin, stateImageSize, stateImageSize, null);		
			g2.drawString(timeString, margin, stateImageSize + margin + labelSize);			
			if (currentIndex == index || hoveredIndex == index) {
				g2.drawRect(0, 0, stateImageSize + 2 * margin - 2, stateImageSize + 2 * margin - 2);
			}
			
			g2.dispose();
		}
		
		public void putState() throws IOException {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			ObjectOutputStream obStream = new ObjectOutputStream(byteStream);
			// serializing objects
			PivotSlice.dataSource.writeObjects(obStream);
			rootFrame.graphCanvas.writeObjects(obStream);
			rootFrame.facetBrowserX.writeObjects(obStream);
			rootFrame.facetBrowserY.writeObjects(obStream);
			
			
			obStream.close();
			
			byteStream.flush();
			storage = byteStream.toByteArray();
			byteStream.close();
		}
		
		public void getState() throws IOException, ClassNotFoundException {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(storage);
			ObjectInputStream obStream = new ObjectInputStream(byteStream);
			// deserializing objects
			PivotSlice.dataSource.readObjects(obStream);
			rootFrame.graphCanvas.readObjects(obStream);
			rootFrame.facetBrowserX.readObjects(obStream);
			rootFrame.facetBrowserY.readObjects(obStream);
			
			
			obStream.close();
			byteStream.close();
		}
	}




}
