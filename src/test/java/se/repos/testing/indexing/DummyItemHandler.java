package se.repos.testing.indexing;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.management.RuntimeErrorException;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.IndexingItemProgress;

public class DummyItemHandler implements IndexingItemHandler {

	private SolrServer dummycore;

	DummyItemHandler(SolrServer dummycore) {
		this.dummycore = dummycore;
	}
	
	@Override
	public void handle(IndexingItemProgress progress) {
		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("id", "" + System.currentTimeMillis() + "" + Math.random());
		try {
			dummycore.add(doc);
			dummycore.commit();
		} catch (SolrServerException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return new HashSet<Class<? extends IndexingItemHandler>>();
	}

}
