package se.repos.indexing.testing.svn;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.indexing.item.IndexingItemHandler;
import se.repos.search.SearchReposItem;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

public class SvnTestIndexing {

	private static SvnTestIndexing instance = null;
	
	private SvnTestIndexing() {
		
	}
	
	public static SvnTestIndexing getInstance() {
		if (instance == null) {
			instance = new SvnTestIndexing();
		}
		return instance;
	}
	
	public SearchReposItem getItem() {
		
		return null;
	}
	
	public SolrServer enable(CmsTestRepository repo) {
		
		return null;
	}
	
	public SolrServer enable(CmsTestRepository repo, IndexingItemHandler... extraSyncHandlers) {
		
		return null;
	}
	
}
