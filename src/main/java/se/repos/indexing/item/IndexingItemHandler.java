package se.repos.indexing.item;

import java.util.Set;

/**
 * Handles one item at a time
 */
public interface IndexingItemHandler {

	public void handle(IndexingItemProgress progress);
	
	/**
	 * @return other handlers that this one depends on, null for no dependencies
	 */
	public Set<Class<? extends IndexingItemHandler>> getDependencies();
	
}
