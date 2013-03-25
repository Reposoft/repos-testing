package se.repos.indexing.twophases;

import java.io.InputStream;

import se.repos.indexing.IndexingDoc;
import se.repos.indexing.item.IndexingItemProgress;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.change.CmsChangesetItem;
import se.simonsoft.cms.item.properties.CmsItemProperties;

public class IndexingItemProgressPhases implements IndexingItemProgress {

	@Override
	public CmsRepository getRepository() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RepoRevision getRevision() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IndexingDoc getFields() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CmsChangesetItem getItem() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CmsItemProperties getProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public InputStream getContents() {
		// TODO Auto-generated method stub
		return null;
	}

}
