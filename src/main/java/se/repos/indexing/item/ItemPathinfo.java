package se.repos.indexing.item;

import java.util.Set;

import se.repos.indexing.IndexingDoc;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.change.CmsChangesetItem;

/**
 * Metadata but not versioned properties about an item,
 * i.e. anything that can be derived from basic path info.
 */
public class ItemPathinfo implements IndexingItemHandler {

	@Override
	public void handle(IndexingItemProgress progress) {
		CmsChangesetItem item = progress.getItem();
		IndexingDoc d = progress.getFields();
		String id = getId(progress.getRepository(), progress.getRevision(), item.getPath());
		d.setField("id", id);
		d.setField("path", item.getPath().toString());
		d.setField("pathname", item.getPath().getName());
	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return null;
	}

	// TODO we need an IdStrategy service, but most likely only one impl
	String getId(CmsRepository repository, RepoRevision revision, CmsItemPath path) {
		return repository.getHost() + repository.getUrlAtHost() + (path == null ? "" : path) + "@" + getIdRevision(revision); 
	}
	
	private String getIdRevision(RepoRevision revision) {
		return Long.toString(revision.getNumber());
	}	
	
}
