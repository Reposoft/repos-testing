package se.repos.indexing.item;

import java.util.Set;

/**
 * Versioned properties of an item,
 * assumed to run after {@link ItemPathinfo}.
 */
public class ItemProperties implements IndexingItemHandler {

	@Override
	public void handle(IndexingItemProgress progress) {
		// TODO Auto-generated method stub
	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		// TODO Auto-generated method stub
		return null;
	}

}
