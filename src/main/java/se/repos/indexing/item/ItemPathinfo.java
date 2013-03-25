package se.repos.indexing.item;

import java.util.Set;

/**
 * Metadata but not versioned properties about an item,
 * i.e. anything that can be derived from basic path info.
 */
public class ItemPathinfo implements IndexingItemHandler {

	@Override
	public void handle(IndexingItemProgress progress) {
		// TODO Auto-generated method stub

	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return null;
	}

}
