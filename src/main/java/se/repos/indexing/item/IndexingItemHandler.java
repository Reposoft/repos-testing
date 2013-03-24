package se.repos.indexing.item;

/**
 * Handles one item at a time
 */
public interface IndexingItemHandler {

	public void handle(IndexingItemProgress progress);
	
}
