package se.repos.indexing;

import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

public interface ReposIndexing {

	public void sync(CmsRepository repository, RepoRevision revision);

	/**
	 * @return Highest revision that indexing has completed for.
	 */
	public RepoRevision getRevComplete(CmsRepository repository);

	/**
	 * @return Highest revision that indexing has started for.
	 */
	public RepoRevision getRevProgress(CmsTestRepository repo);
	
}
