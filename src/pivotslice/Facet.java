package pivotslice;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import javax.swing.ImageIcon;

import com.microsoft.research.Author;
import com.microsoft.research.Conference;
import com.microsoft.research.Journal;
import com.microsoft.research.Keyword;
import com.microsoft.research.Publication;

public class Facet implements Serializable {

	private static final long serialVersionUID = -8965911167357391727L;

	public static enum FacetType {CATEGORICAL, NUMERICAL};
	
	public String facetName;
	public int facetID;
	public FacetType facetType;
	public Color facetColor;
	public Color facetBrightColor;
	public ImageIcon facetIcon;
	
	public static Facet[] availableFacets = initAvailableFacets();
	
	public Facet(String name, int id, FacetType type) {
		facetName = name;
		facetID = id;
		facetType = type;
		facetIcon = new ImageIcon(PivotSlice.class.getResource("/images/" + facetName.toLowerCase() + ".png"));
	}
	
	public static void sortPublications(LinkedList<Publication> pubs, final Facet f) {
		if (f.facetType == FacetType.CATEGORICAL) 
			return;
		
		Collections.sort(pubs, new Comparator<Publication>() {
			@Override
			public int compare(Publication pub1, Publication pub2) {
				int v1 = 0, v2 = 0;
				switch (f.facetID) {
				case 3:
					v1 = pub1.getYear().intValue();
					v2 = pub2.getYear().intValue();
					break;
				case 5:
					v1 = pub1.getCitationCount().intValue();
					v2 = pub2.getCitationCount().intValue();
					break;
				case 6:
					v1 = pub1.getReferenceCount().intValue();
					v2 = pub2.getReferenceCount().intValue();
					break;
				case 7:
					v1 = PivotSlice.dataSource.getNetwork().graphEdgesIn.get(pub1.getID()).size();
					v2 = PivotSlice.dataSource.getNetwork().graphEdgesIn.get(pub2.getID()).size();
					break;
				case 8:
					v1 = PivotSlice.dataSource.getNetwork().graphEdgesOut.get(pub1.getID()).size();
					v2 = PivotSlice.dataSource.getNetwork().graphEdgesOut.get(pub2.getID()).size();
					break;
				}
				
				return v1 - v2;
			}			
		});
	}
	
	public static FacetType getFacetType(int facetID) {
		switch (facetID) {
		case 0:
		case 1:
		case 2:
		case 4:
			return FacetType.CATEGORICAL;
		case 3:
		case 5:
		case 6:
		case 7:
		case 8:
			return FacetType.NUMERICAL;
		default:
			return null;
		}
	}
	
	public static int getNumericalFacetValue(Publication pub, int facetID) {
		switch (facetID) {
		case 3:
			return pub.getYear().intValue();
		case 5:
			return pub.getCitationCount().intValue();
		case 6:
			return pub.getReferenceCount().intValue();
		case 7:
			return PivotSlice.dataSource.getNetwork().graphEdgesIn.get(pub.getID()).size();
		case 8:
			return PivotSlice.dataSource.getNetwork().graphEdgesOut.get(pub.getID()).size();
		default:
			return -1;
		}
	}
	
	public static int getNumericalFacetValue(Publication pub, Facet f) {
		return getNumericalFacetValue(pub, f.facetID);
	}
	
	public static String getNumericalFacetString(Publication pub, int facetID) {
		// for numerical attributes
		int value = getNumericalFacetValue(pub, facetID);
		if (value == -1)
			return null;
		else
			return String.format("%d", value);
	}
	
	public static LinkedList<String> getCategoricalFacetStrings(Publication pub, int facetID) {
		// for categorical attributes
		LinkedList<String> strs = new LinkedList<String>();
		
		switch (facetID) {
		case 0:
			for (Author au : pub.getAuthor()) {
				strs.add(au.getFirstName() + " " + au.getLastName());
			}
			break;
		case 1:
			if(pub.getJournal() != null)
				strs.add(pub.getJournal().getFullName());
			break;
		case 2:
			if(pub.getConference() != null)
				strs.add(pub.getConference().getFullName());
			break;
		case 4:
			for (Keyword word : pub.getKeyword()) {
				strs.add(word.getName());
			}
			break;
		}
		
		return strs;
	}
	
	public static String getCategoricalFacetString(Long id, int facetID) {
		Object ob = PivotSlice.dataSource.getAttributeMaps(facetID).get(id);
		switch (facetID) {
		case 0:
			Author au = (Author)ob;
			return au.getFirstName() + " " + au.getLastName();
		case 1:
			Journal jo = (Journal)ob;
			return jo.getFullName();
		case 2:
			Conference co = (Conference)ob;
			return co.getFullName();
		case 4:
			Keyword ke = (Keyword)ob;
			return ke.getName();
		default:
			return null;
		}	
	}
	
	public static LinkedList<Long> getCategoricalFacetValueIDs(Publication pub, Facet facet) {
		return getCategoricalFacetValueIDs(pub, facet.facetID);
	}
	
	public static LinkedList<Long> getCategoricalFacetValueIDs(Publication pub, int facetID) {
		LinkedList<Long> ids = new LinkedList<Long>();
		
		switch (facetID) {
		case 0:
			for (Author au : pub.getAuthor())
				ids.add(au.getID());
			break;
		case 1:
			if (pub.getJournal() != null)
				ids.add(pub.getJournal().getID());
			break;
		case 2:
			if (pub.getConference() != null)
				ids.add(pub.getConference().getID());
			break;
		case 4:
			for (Keyword key : pub.getKeyword()) 
				ids.add(key.getID());
			break;
		}	
		
		return ids;
	}
	
	public static Long getCategoricalFacetValueID(Publication pub, int facetID, int index) {
		Long id = null;
		switch (facetID) {
		case 0:
			id = pub.getAuthor().get(index).getID();
			break;
		case 1:
			if (pub.getJournal() != null)
				id = pub.getJournal().getID();
			break;
		case 2:
			if (pub.getConference() != null)
				id = pub.getConference().getID();
			break;
		case 4:
			id = pub.getKeyword().get(index).getID();
			break;
		}	
		
		return id;
	}
	
	private static Facet[] initAvailableFacets() {		
		Facet[] facets = new Facet[9];
		
		facets[0] = new Facet("Author", 0, Facet.FacetType.CATEGORICAL);
		facets[1] = new Facet("Journal", 1, Facet.FacetType.CATEGORICAL);
		facets[2] = new Facet("Conference", 2, Facet.FacetType.CATEGORICAL);
		facets[3] = new Facet("Year", 3, Facet.FacetType.NUMERICAL);
		facets[4] = new Facet("Keyword", 4, Facet.FacetType.CATEGORICAL);
		facets[5] = new Facet("Citation", 5, Facet.FacetType.NUMERICAL);
		facets[6] = new Facet("Reference", 6, Facet.FacetType.NUMERICAL);
		facets[7] = new Facet("In-degree", 7, Facet.FacetType.NUMERICAL);
		facets[8] = new Facet("Out-degree", 8, Facet.FacetType.NUMERICAL);
		
		int[] rgbs = new int[] {
				/*
				228, 26, 28, 
				247, 129, 191,
				152, 78, 163, 				 
				255, 255, 51, 
				166, 86, 40, 
				77, 175, 74,
				255, 127, 0*/
				251, 128, 114,
				141, 211, 199, 			 
				190, 186, 218,
				255, 255, 179,
				128, 177, 211, 
				179, 222, 105,
				253, 180, 98,
				204, 235, 197,
				254, 217, 166
		};
		
		for (int i = 0; i < facets.length; i++) {
			facets[i].facetColor = new Color(rgbs[i * 3], rgbs[i * 3 + 1], rgbs[i * 3 + 2]);
			facets[i].facetBrightColor = new Color(rgbs[i * 3] + (256 - rgbs[i * 3]) / 2, 
					rgbs[i * 3 + 1] + (256 - rgbs[i * 3 + 1]) / 2, 
					rgbs[i * 3 + 2] + (256 - rgbs[i * 3 + 2]) / 2);
		}
		
		return facets;
	}
}
