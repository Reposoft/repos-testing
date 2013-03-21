package se.repos.indexing;

import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;

public interface ReposIndexing {

	public void sync(CmsRepository repository, RepoRevision revision);
	
	public RepoRevision getRevComplete(CmsRepository repository);
	
}
