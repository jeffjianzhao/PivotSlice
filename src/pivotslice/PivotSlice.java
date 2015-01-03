package pivotslice;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


public class PivotSlice extends JFrame {

	private static final String appTitle = "PivotSlice";	
	private static final int frameWidth = 1280, frameHeight = 960;
	
	public static final ImageIcon icon = new ImageIcon(PivotSlice.class.getResource("/images/network-icon.png"));
	public static final DataSource dataSource = new DataSource();
	
	public final SearchPanel searchPanel;
	public final OperationPanel opPanel;
	public final GraphCanvas graphCanvas;
	public final FacetBrowser facetBrowserY, facetBrowserX;
	public final JPanel centerPanel;
	public final GridPanel gridPanel;
	public final InfoPanel infoPanel;
	public final HistoryPanel historyPanel;
	public final AutoLogger logger;
	
	public final MyGlassPane glassPane = new MyGlassPane();
	public final MyGlobalDragListener dragListener = new MyGlobalDragListener();
	
	public PivotSlice(boolean logging) {
		super(appTitle);
		
		// basic settings
		// ToolTipManager.sharedInstance().setInitialDelay(0);
		this.setIconImage(icon.getImage());
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			showErrorMessage("UI error:\n" + e.toString());
			e.printStackTrace();
		}
		this.setGlassPane(glassPane);
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				int result = showConfirmMessage("Do you want to save current data before exiting?");
				if (result == JOptionPane.OK_OPTION) {
					// save dataset to file
					JFileChooser fc = new JFileChooser();
					int returnVal = fc.showSaveDialog(PivotSlice.this);
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						try {
							dataSource.saveNetwork(fc.getSelectedFile());
						} catch (Exception e1) {
							showErrorMessage("Save Error:\n" + e1.toString());
							e1.printStackTrace();
						}
					}
				}
				
				logger.stop();
				PivotSlice.this.setVisible(false);
				PivotSlice.this.dispose();
			}
		});		
		
		// north panel
		searchPanel = new SearchPanel(this);
		opPanel = new OperationPanel(this);
		historyPanel = new HistoryPanel(this);
		
		JPanel northwest = new JPanel();
		northwest.setBorder(BorderFactory.createTitledBorder("Search and Operation"));
		northwest.setLayout(new BoxLayout(northwest, BoxLayout.Y_AXIS));
		northwest.add(searchPanel);
		northwest.add(opPanel);
		
		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.add(historyPanel, BorderLayout.CENTER);
		northPanel.add(northwest, BorderLayout.WEST);
		
		// center panel
		graphCanvas = new GraphCanvas(this);
		
		facetBrowserX = new FacetBrowser(FacetBrowser.Direction.HORIZONTAL, this);
		facetBrowserX.setPreferredSize(new Dimension(400, 120));
		facetBrowserY = new FacetBrowser(FacetBrowser.Direction.VERTICAL, this);
		facetBrowserY.setPreferredSize(new Dimension(120, 400));
		
		gridPanel = new GridPanel(this);
		
		centerPanel = new JPanel(new GridBagLayout());
		centerPanel.setBorder(BorderFactory.createTitledBorder("Main Canvas"));
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		c.gridx = 0;
		c.gridy = 0;
		c.gridheight = 3;
		c.gridwidth = 1;
		centerPanel.add(facetBrowserY, c);
		
		c.gridx = 1;
		c.gridy = 3;
		c.gridheight = 1;
		c.gridwidth = 3;
		centerPanel.add(facetBrowserX, c);
		
		c.gridx = 0;
		c.gridy = 3;
		c.gridheight = 1;
		c.gridwidth = 1;
		centerPanel.add(gridPanel, c);
		
		c.gridx = 1;
		c.gridy = 0;
		c.gridheight = 3;
		c.gridwidth = 3;
		c.weightx = 1;
		c.weighty = 1;
		centerPanel.add(graphCanvas, c);
		
		// east panel
		infoPanel = new InfoPanel(this);
		JScrollPane eastPanel = new JScrollPane(infoPanel);
		eastPanel.setPreferredSize(new Dimension(200, 600));
		eastPanel.setBorder(BorderFactory.createTitledBorder("Information"));
		eastPanel.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		eastPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		// add panels to the view
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(northPanel, BorderLayout.NORTH);
		this.getContentPane().add(centerPanel, BorderLayout.CENTER);
		this.getContentPane().add(eastPanel, BorderLayout.EAST);
		
		logger = new AutoLogger(logging);
		logger.start();
	}
	
	public void renderDefaultDataset(String defaultDataPath) {
		if (defaultDataPath != null) {
		    // load initial dataset
			try {
				dataSource.initNetwork(new File(defaultDataPath));
			} catch (Exception e) {
				showErrorMessage("IO error:\n" + e.toString());
				e.printStackTrace();
			}
		}
		// ready to render the default dataset
		graphCanvas.initGraphCanvas();
		
		if (defaultDataPath != null)
			historyPanel.addState();
	}
	
	public void showErrorMessage(String msg) {
		JOptionPane.showMessageDialog(this, msg, appTitle, JOptionPane.ERROR_MESSAGE);
	}
	
	public void showInfoMessage(String msg) {
		JOptionPane.showMessageDialog(this, msg, appTitle, JOptionPane.INFORMATION_MESSAGE);
	}
	
	public int showConfirmMessage(String msg) {
		int result = JOptionPane.showConfirmDialog(this, msg, appTitle, JOptionPane.OK_CANCEL_OPTION);
		return result;
	}
	
	public static BufferedImage getScreenShot(Component component) {
		BufferedImage image = new BufferedImage(component.getWidth(),
				component.getHeight(), BufferedImage.TYPE_INT_ARGB);
		component.paint(image.getGraphics());
		return image;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// inner classes
	public class MyGlassPane extends JComponent {
		private Point point = new Point(0, 0);
		private BufferedImage image;
		
		public void setPoint(Point p) {
			point = p;
		}
		
		public void setImage(BufferedImage img) {
			image = img;
		}
		
		protected void paintComponent(Graphics g) {
			if (image != null) {
				Graphics2D g2 = (Graphics2D)g;
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
	            g2.drawImage(image, null, point.x, point.y);
	        }
		}
	}
	
	public class MyGlobalDragListener extends DragSourceAdapter implements DragSourceMotionListener {
		private boolean firstCall = true;
		
		public void dragDropEnd(DragSourceDropEvent dsde) {
			glassPane.setVisible(false);
			firstCall = true;
		}
		
		public void dragMouseMoved(DragSourceDragEvent dsde) {
			glassPane.setPoint(new Point(dsde.getLocation().x - PivotSlice.this.getX() + 5, 
					dsde.getLocation().y - PivotSlice.this.getY() + 5));
			if (firstCall) {
				glassPane.setVisible(true);
				firstCall = false;
			}
			glassPane.repaint();
		}

	}
	
	public static void showFrame(final String filename, boolean logging) {
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();		
		final PivotSlice f = new PivotSlice(logging);
		
		if (dim.width < frameWidth || dim.height < frameHeight)
			f.setSize(dim.width, dim.height);
		else
			f.setSize(frameWidth, frameHeight);
		f.setLocation(dim.width/2 - frameWidth/2, dim.height/2 - frameHeight/2);		
		//f.pack();
		f.setVisible(true);
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				f.renderDefaultDataset(filename);
			}
		});
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// the main method
	public static void main(String[] args) {
		if (args.length == 0) {
			showFrame(null, false);
		}
		else if (args.length == 1) {
			if (args[0].equals("-l"))
				showFrame(null, true);
			else
				showFrame(args[0], false);
		}
		else if (args.length == 2 && args[0].equals("-l")) {
			showFrame(args[1], true);
		}
	}
}
