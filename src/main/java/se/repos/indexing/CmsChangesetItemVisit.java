package se.repos.indexing;

import java.io.InputStream;

import se.simonsoft.cms.item.events.change.CmsChangesetItem;

public interface CmsChangesetItemVisit {

	CmsChangesetItem getItem();
	
	InputStream getContents();
	
}
