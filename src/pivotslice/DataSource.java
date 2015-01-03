package pivotslice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.datacontract.schemas._2004._07.libra_service.ReferenceRelationship;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.microsoft.research.Author;
import com.microsoft.research.Keyword;
import com.microsoft.research.PagedList;
import com.microsoft.research.Publication;
import com.microsoft.research.query.AcademicSearchQuery;
import com.microsoft.research.query.AcademicSearchQueryFactory;
import com.microsoft.research.query.PublicationSearchQuery;

public class DataSource {
	
	private static final String appid = "1479ed99-1011-47b4-a0b7-588d7bded87f";
	
	private AcademicSearchQueryFactory factory;
	private Network network = new Network();
	private ArrayList<HashMap<Long, Object>> attributeMaps = new ArrayList<HashMap<Long, Object>>(4);
	
	public DataSource() {
		factory = AcademicSearchQueryFactory.newInstance(appid);
		for (int i = 0; i < 4; i++)		// the number of categorical facets: hard-coded
			attributeMaps.add(new HashMap<Long, Object>());
	}
	
	public AcademicSearchQueryFactory getQueryFactory() {
		return factory;
	}
	
	public PublicationSearchQuery getNewQuery() { 
		return  factory.newPublicationSearchQuery();
	}
	
	public PagedList<Publication> executeQuery(AcademicSearchQuery<Publication> query) {
		return query.list(); 
	}
	
	public Network getNetwork() {
		return network;
	}
	
	public HashMap<Long, Object> getAttributeMaps(int fid) {
		switch(fid) {
			case 0:
				return attributeMaps.get(0);
			case 1:
				return attributeMaps.get(1);
			case 2:
				return attributeMaps.get(2);
			case 4:
				return attributeMaps.get(3);
			default:
				return null;
		}
	}
	
	public void writeObjects(ObjectOutputStream obs) throws IOException {
		obs.writeObject(network);
		obs.writeObject(attributeMaps);
	}
	
	public void readObjects(ObjectInputStream obs) throws IOException, ClassNotFoundException {
		network = (Network) obs.readObject();
		attributeMaps = (ArrayList<HashMap<Long, Object>>) obs.readObject();
	}
	
	public void initNetwork(File f) throws Exception {
		if (f.getName().endsWith(".json")) {	// json format
			FileReader fin = new FileReader(f);
			Gson gson = new GsonBuilder().create();
			network = gson.fromJson(fin, Network.class);
			fin.close();
		}
		else {	// binary format
			ObjectInputStream obin = new ObjectInputStream(new FileInputStream(f));
			network.graphNodes = (HashMap<Long, Publication>)obin.readObject();
			network.graphEdgesIn = (HashMap<Long, HashSet<Long>>)obin.readObject();
			network.graphEdgesOut = (HashMap<Long, HashSet<Long>>)obin.readObject();
			obin.close();
		}
		updateAttributeMaps();
		
		/*
		HashMap<String, Publication> graphNodes = null;
		HashMap<String, LinkedList<String>> graphEdges = null;
		// load the data in binary format
		FileInputStream fin = new FileInputStream(filename);
		ObjectInputStream obin = new ObjectInputStream(fin);
		
		graphNodes = (HashMap<String, Publication>)obin.readObject();
		graphEdges = (HashMap<String, LinkedList<String>>)obin.readObject();
		
		if(graphNodes != null && graphEdges != null) {
			// insert the nodes
			Iterator<Entry<String, Publication>> iter1 = graphNodes.entrySet().iterator();
			while (iter1.hasNext()) {
				Map.Entry<String, Publication> item = iter1.next();
				Publication pub = item.getValue();
				network.graphNodes.put(pub.getID(), pub);
				network.graphEdgesIn.put(pub.getID(), new LinkedList<Long>());
				network.graphEdgesOut.put(pub.getID(), new LinkedList<Long>());
			}
			// build the edges
			Iterator<Entry<String, LinkedList<String>>> iter2 = graphEdges.entrySet().iterator();
			while (iter2.hasNext()) {
				Map.Entry<String, LinkedList<String>> item = iter2.next();
				Publication fromPub = graphNodes.get(item.getKey());				
				LinkedList<Long> references = network.graphEdgesOut.get(fromPub.getID());
				
				for(String pubid : item.getValue()) {
					Publication toPub = graphNodes.get(pubid);
					references.add(toPub.getID()); // references									
					network.graphEdgesIn.get(toPub.getID()).add(fromPub.getID()); // citations		
				}
			}
		}
		*/
	}
	
	public void addToNetwork(List<Publication> publications, ProgressTask task) {
		// insert into the network
		network.newGraphNodes.clear();
		for(Publication pub : publications) {
			Long id = pub.getID();
			if (!network.graphNodes.containsKey(id)) {
				network.newGraphNodes.put(id, pub);
				network.graphNodes.put(id, pub);
				network.graphEdgesIn.put(id, new HashSet<Long>());
				network.graphEdgesOut.put(id, new HashSet<Long>());
			}
		}
		
		int i = 1;
		for (Publication pub : publications) {
			// citations
			PagedList<Publication> response = factory.newPublicationSearchQuery().withPublicationId(pub.getID().intValue())
				.withReferenceRelationship(ReferenceRelationship.CITATION)
				.withStartIndex(1).withEndIndex(100).list();
			
			for (Publication citepub : response) {
				if (network.graphNodes.containsKey(citepub.getID())) {
					network.graphEdgesOut.get(citepub.getID()).add(pub.getID());	
					network.graphEdgesIn.get(pub.getID()).add(citepub.getID());
				}
			}
			
			// references
			response = factory.newPublicationSearchQuery().withPublicationId(pub.getID().intValue())
					.withReferenceRelationship(ReferenceRelationship.REFERENCE)
					.withStartIndex(1).withEndIndex(100).list();
			
			for (Publication refpub : response) {
				if (network.graphNodes.containsKey(refpub.getID())) {
					network.graphEdgesOut.get(pub.getID()).add(refpub.getID()); 
					network.graphEdgesIn.get(refpub.getID()).add(pub.getID());
				}	
			}
			
			task.advanceProgress(i * 100 / publications.size());
			i++;
		}
		
		updateAttributeMaps();
	}
	
	public void pruneNetwork(Set<Publication> pubs) {
		network.graphNodes.clear();
		for (Publication pub : pubs) {
			network.graphNodes.put(pub.getID(), pub);
		}
		
		HashMap<Long, HashSet<Long>> newEdgeOut = new HashMap<Long, HashSet<Long>>();
		HashMap<Long, HashSet<Long>> newEdgeIn = new HashMap<Long, HashSet<Long>>();
		for (Publication pub : pubs) {
			HashSet<Long> edges = new HashSet<Long>();
			for (Long id : network.graphEdgesOut.get(pub.getID())) {
				if (network.graphNodes.containsKey(id)) 
					edges.add(id);
			}
			newEdgeOut.put(pub.getID(), edges);
			
			edges = new HashSet<Long>();
			for (Long id : network.graphEdgesIn.get(pub.getID())) {
				if (network.graphNodes.containsKey(id)) 
					edges.add(id);
			}
			newEdgeIn.put(pub.getID(), edges);
		}
		network.graphEdgesOut = newEdgeOut;
		network.graphEdgesIn = newEdgeIn;
		
		network.newGraphNodes.clear();
		
		updateAttributeMaps();
	}
	
	public void updateAttributeMaps() {
		for (HashMap<Long, Object> map : attributeMaps)
			map.clear();
		
		for (Publication pub : network.graphNodes.values()) {
			for (Author au : pub.getAuthor()) {
				if (!attributeMaps.get(0).containsKey(au.getID()))
					attributeMaps.get(0).put(au.getID(), au);
			}
			if (pub.getJournal() != null && !attributeMaps.get(1).containsKey(pub.getJournal().getID())) {
				attributeMaps.get(1).put(pub.getJournal().getID(), pub.getJournal());
			}
			if (pub.getConference() != null && !attributeMaps.get(2).containsKey(pub.getConference().getID())) {
				attributeMaps.get(2).put(pub.getConference().getID(), pub.getConference());
			}
			for (Keyword key : pub.getKeyword()) {
				if (!attributeMaps.get(3).containsKey(key.getID()))
					attributeMaps.get(3).put(key.getID(), key);
			}
		}
	}
	
	public void saveNetwork(File file) throws Exception {
		network.newGraphNodes.clear();
		
		if (file.getName().endsWith(".json")) {
			Gson gson = new GsonBuilder().create();
			FileWriter fw = new FileWriter(file);
			gson.toJson(network, fw);
			fw.flush();
			fw.close();
			//gson.toJson(network, new FileWriter(file));
		}
		else {
			ObjectOutputStream obout = new ObjectOutputStream(new FileOutputStream(file));
			obout.writeObject(network.graphNodes);
			obout.writeObject(network.graphEdgesIn);
			obout.writeObject(network.graphEdgesOut);
			obout.close();
		}
	}
	
	public static class Network implements Serializable {
		private static final long serialVersionUID = 5242465010409928475L;
		
		// publications
		public HashMap<Long, Publication> graphNodes = new HashMap<Long, Publication>();
		// references
		public HashMap<Long, HashSet<Long>> graphEdgesOut = new HashMap<Long, HashSet<Long>>();	
		// citations
		public HashMap<Long, HashSet<Long>> graphEdgesIn = new HashMap<Long, HashSet<Long>>();
		// newly added publications
		public HashMap<Long, Publication> newGraphNodes = new HashMap<Long, Publication>();
	}
	
	
	public static interface ProgressTask {
		public void advanceProgress(int progress);
	}
	
}
