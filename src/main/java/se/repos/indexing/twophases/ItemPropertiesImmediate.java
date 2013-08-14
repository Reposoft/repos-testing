package se.repos.indexing.twophases;

import javax.inject.Inject;

import se.repos.indexing.IndexingDoc;
import se.repos.indexing.item.ItemProperties;
import se.repos.indexing.item.ItemPropertiesBufferStrategy;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.inspection.CmsContentsReader;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;

/**
 * Reads properties to memory immediately, assuming they are rather small and will always be needed.
 */
public class ItemPropertiesImmediate implements ItemPropertiesBufferStrategy {

	private CmsContentsReader reader;
	
	@Inject
	public void setCmsContentsReader(CmsContentsReader reader) {
		this.reader = reader;
	}

	@Override
	public ItemProperties getBuffer(CmsRepositoryInspection repository,
			RepoRevision revision, CmsItemPath path, IndexingDoc pathinfo) {
		throw new UnsupportedOperationException("not implemented");
	}
	
}
