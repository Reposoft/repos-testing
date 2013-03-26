package se.repos.indexing.item;

import java.io.InputStream;

/**
 * For reading contents in multiple indexers for the same item.
 * 
 * Alternatively the buffering could be built into the {@link IndexingItemProgress} impl.
 */
public interface ItemContentsBuffer {

	InputStream getContents();
	
}
