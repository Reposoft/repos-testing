/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

/**
 * To avoid a runtime dependency on Guice in all modules that use this test framework
 * we hard code dependency injection for the blocking indexer needed in hooks.
 * @deprecated Currently done in IndexingOptions
 */
public class EmbeddedIndexingContext {
	
	public void run() {
		
	}
	
}
