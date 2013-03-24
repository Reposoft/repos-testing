package se.repos.indexing.testing.svn;

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
	
	public SearchReposItem enable(CmsTestRepository repo) {
		
		return null;
	}
	
	public SearchReposItem enable(CmsTestRepository repo, IndexingItemHandler... extraSyncHandlers) {
		
		return null;
	}
	
}
