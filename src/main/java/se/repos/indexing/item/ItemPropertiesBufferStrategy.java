package se.repos.indexing.item;

import se.repos.indexing.IndexingDoc;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;

public interface ItemPropertiesBufferStrategy {

	/**
	 * Called for files and folders, returns per pegged item access to versioned metadata.
	 * @param repository
	 * @param revision
	 * @param path
	 * @param pathinfo Can be used to get size info etc.
	 * @return TODO what?
	 */
	Object getBuffer(CmsRepositoryInspection repository, RepoRevision revision, CmsItemPath path, IndexingDoc pathinfo);
	
}
