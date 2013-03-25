package se.repos.indexing.item;

import java.util.HashSet;
import java.util.Set;

/**
 * Sets value in the "patharea" field, often overridded,
 * this impl recognizes the conventional svn trunk/branches/tags.
 */
public class ItemPathareaSvnConventions implements IndexingItemHandler {

	@Override
	public void handle(IndexingItemProgress progress) {
		
	}

	@SuppressWarnings("serial")
	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return new HashSet<Class<? extends IndexingItemHandler>>() {{
			add(ItemPathinfo.class);
		}};
	}

}
