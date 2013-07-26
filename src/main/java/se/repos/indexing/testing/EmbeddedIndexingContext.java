package se.repos.indexing.testing;

/**
 * To avoid a runtime dependency on Guice in all modules that use this test framework
 * we hard code dependency injection for the blocking indexer needed in hooks. 
 */
public class EmbeddedIndexingContext {
	
	public void run() {
		
	}
	
}
