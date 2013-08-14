package se.repos.indexing.item;

import se.repos.indexing.IndexingDoc;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;

public interface ItemContentsBufferStrategy {

	/**
	 * Called only for files, returns per pegged item access to content.
	 * @param repository
	 * @param revision
	 * @param path
	 * @param pathinfo
	 * @return
	 */
	ItemContentsBuffer getBuffer(CmsRepositoryInspection repository, RepoRevision revision, CmsItemPath path, IndexingDoc pathinfo);
	
}
