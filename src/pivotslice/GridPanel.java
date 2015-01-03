package pivotslice;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

public class GridPanel extends JPanel implements MouseListener{

	private static final int margin = 16;
	//private static final float alpha = 0.6f;
	//private static final Color color1 = new Color(237, 248, 177);
	//private static final Color color2 = new Color(44, 127, 184);
	private static final int[] edgeColor = new int[]{150, 150, 150};
	private static final int[] edgeOutColor = new int[]{77, 175, 74};
	private static final int[] edgeInColor = new int[]{255, 127, 0};
	private static final int[] nodeColor = new int[]{25, 25, 112};
	private static final Color gridColor = new Color(100, 100, 100, 50);
	private static final Color cellSelColor = new Color(220, 20, 60);
	
	private int colorMapType = 0;	// 0 - edge, 1 - edgeOut, 2 - edgeIn, 3 - node
	private int xGridLen = 0, yGridLen = 0;
	private int xSel = -1, ySel = -1;
	private int xHov = -1, yHov = -1;
	private GridRect[] gridRects;
	private Polygon leftArrow = new Polygon(new int[]{3, 13, 13}, new int[]{60, 55, 65}, 3);
	private Polygon rightArrow = new Polygon(new int[]{107, 107, 117}, new int[]{55, 65, 60}, 3);
	
	private final PivotSlice rootFrame;
	
	public GridPanel(PivotSlice frame) {
		rootFrame = frame;
		this.addMouseListener(this);
		this.setBackground(Color.WHITE);
	}
	
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		if (xSel == -1 || ySel == -1)
			return;
		
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		// draw controls
		g2.setColor(Color.black);
		g2.fill(leftArrow);
		g2.fill(rightArrow);
		
		// draw color mapping
		for (int i = 0; i < gridRects.length; i++) {
			GridRect rect = gridRects[i];
			
			switch(colorMapType) {
			case 0:
				g2.drawString("total edges", 20, 118);
				g2.setPaint(new Color(edgeColor[0], edgeColor[1], edgeColor[2], (int)(rect.edgeVal * 200) + 30));
				break;
			case 1:
				g2.drawString("in-coming edges", 20, 118);
				g2.setPaint(new Color(edgeOutColor[0], edgeOutColor[1], edgeOutColor[2], (int)(rect.edgeOutVal * 200) + 30));
				break;
			case 2:
				g2.drawString("out-going edges", 20, 118);
				g2.setPaint(new Color(edgeInColor[0], edgeInColor[1], edgeInColor[2], (int)(rect.edgeInVal * 200) + 30));
				break;
			case 3:
				g2.drawString("duplicated nodes", 20, 118);
				g2.setPaint(new Color(nodeColor[0], nodeColor[1], nodeColor[2], (int)(rect.nodeVal * 200) + 30));
				break;
			}
			
			g2.setPaint(rect.color);
			g2.fill(rect.rectangle);

			g2.setPaint(gridColor);
			g2.draw(rect.rectangle);
		}
		
		// draw hover-over cell
		if (xHov != -1 && yHov != -1 && (xHov != xSel || yHov != ySel) 
				&& xHov + yHov * xGridLen < gridRects.length) {
			GridRect selRect = gridRects[xHov + yHov * xGridLen];
			g2.setPaint(Color.BLACK);
			g2.draw(selRect.rectangle);
		}
		
		// draw selected cell
		GridRect selRect = gridRects[xSel + ySel * xGridLen];
		g2.setPaint(cellSelColor);
		g2.draw(selRect.rectangle);
		
		g2.dispose();
	}
	
	public void setHoveredGrid(GraphCell hcell) {
		if (hcell != null) {
			xHov = hcell.gridx;
			yHov = hcell.gridy;
		}
		else {
			xHov = -1;
			yHov = -1;
		}
		this.repaint();
	}
	
	public void updateDrawings() {
		// set up basic values
		GraphCell cell = rootFrame.graphCanvas.getSelectedCell(); 
		if (cell == null) {
			xSel = ySel = -1;
			this.repaint();
			return;
		}		
		
		boolean updated = false;
		if (xGridLen != rootFrame.graphCanvas.getColLength() 
				|| yGridLen != rootFrame.graphCanvas.getRowLength()) {
			updated = true;
			xGridLen = rootFrame.graphCanvas.getColLength();
			yGridLen = rootFrame.graphCanvas.getRowLength();
			// create grid rects
			Dimension dim = this.getSize();
			double w = (dim.getWidth() - 2 * margin) / xGridLen;
			double h = (dim.getHeight() - 2 * margin) / yGridLen;
			
			gridRects = new GridRect[xGridLen * yGridLen];
			for (int i = 0; i < xGridLen; i++) {
				for (int j = 0; j < yGridLen; j++) {
					GridRect rect = new GridRect();
					rect.rectangle = new Rectangle2D.Double(margin + i * w, margin + j * h, w, h);				
					gridRects[i + j * xGridLen] = rect;
				}
			}
		}
		
		if (xSel != cell.gridx || ySel != cell.gridy || updated) {
			xSel = cell.gridx; 
			ySel = cell.gridy;	
			
			float maxEdge = 0, maxEdgeIn = 0, maxEdgeOut = 0, maxNode = 0;
			for (int i = 0; i < xGridLen; i++) {
				for (int j = 0; j < yGridLen; j++) {
					GraphCell thecell = rootFrame.graphCanvas.getGraphCell(j, i);
					
					gridRects[i + j * xGridLen].edgeInVal = thecell.edgeCrossIn / (float)thecell.edgeTotal;
					gridRects[i + j * xGridLen].edgeOutVal = thecell.edgeCrossOut / (float)thecell.edgeTotal;
					gridRects[i + j * xGridLen].nodeVal = thecell.dupNode / (float)thecell.publications.size();
					gridRects[i + j * xGridLen].edgeVal = (thecell.edgeCrossIn + thecell.edgeCrossOut) / (float)thecell.edgeTotal;
					
					if (gridRects[i + j * xGridLen].edgeVal > maxEdge)
						maxEdge = gridRects[i + j * xGridLen].edgeVal;
					if (gridRects[i + j * xGridLen].edgeInVal > maxEdgeIn)
						maxEdgeIn = gridRects[i + j * xGridLen].edgeInVal;
					if (gridRects[i + j * xGridLen].edgeOutVal > maxEdgeOut)
						maxEdgeOut = gridRects[i + j * xGridLen].edgeOutVal;
					if (gridRects[i + j * xGridLen].nodeVal > maxNode)
						maxNode = gridRects[i + j * xGridLen].nodeVal;
				}
			}
			
			// normalizing
			for (GridRect rect : gridRects) {
				rect.edgeInVal /= maxEdgeIn;
				rect.edgeOutVal /= maxEdgeOut;
				rect.nodeVal /= maxNode;
				rect.edgeVal /= maxEdge;
			}
			
			// assign colors
			/*
			for (GridRect rect : gridRects) {
				float val = (float)rect.totalEdgeNum / maxEdgeNum;
				if (maxNodeNum != 0)
					val = val * 0.5f + 0.5f * rect.totalDupNodeNum / maxNodeNum;
				float[] c1 = color1.getColorComponents(null);
				float[] c2 = color2.getColorComponents(null);
				rect.color = new Color(c1[0] + (c2[0] - c1[0]) * val, 
						c1[1] + (c2[1] - c1[1]) * val, c1[2] + (c2[2] - c1[2]) * val, alpha);
			}
			*/
		}
		
		this.repaint();
	}
	
	@Override
	public void mouseClicked(MouseEvent me) {
		Point p = me.getPoint();
		if (leftArrow.contains(p) && colorMapType > 0)
			colorMapType--;
		else if (rightArrow.contains(p) && colorMapType < 3)
			colorMapType++;
		
		this.repaint();
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}
	
	private class GridRect {
		public Rectangle2D.Double rectangle;
		public Color color;
		public float edgeInVal;
		public float edgeOutVal;
		public float nodeVal;
		public float edgeVal;
	}


}
