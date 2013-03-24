package se.repos.indexing.item;

import java.io.InputStream;

import se.repos.indexing.IndexingDoc;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;

public interface IndexingItemProgress {

	CmsRepository getRepository();
	
	RepoRevision getRevision();
	
	/**
	 * @return Current index state for the item
	 */
	IndexingDoc getFields();
	
	/**
	 * @return the item that is indexed, with commit revision being the revision that is currently indexed
	 */
	CmsItem getItem();
	
	/**
	 * Called by indexers who need to read contents.
	 * Standard getContents, {@link CmsItem#getContents(java.io.OutputStream)}, may also be supported
	 * but is designed for remote access and is probably not buffered per indexing item. 
	 * @return to be opened and closed by the caller.
	 */
	InputStream getContents();
	
}
