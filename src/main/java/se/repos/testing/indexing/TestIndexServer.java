/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import org.apache.solr.client.solrj.SolrServer;

/**
 * It is quite likely that, as development of applications continue, we'll need a more full fledged server than that used in test cases.
 * Which is why we try to abstract the actual server set up.
 * 
 * Server should not ensure reuse, because that is up to the test framework.
 */
public interface TestIndexServer {

	/**
	 * Makes server ready for tests, verifies that {@link #destroy()} is called between tests.
	 */
	void beforeTest(TestIndexOptions options);
	
	/**
	 * Get a core for use in current test, with existing content if reused since {@link #beforeTest(TestIndexOptions)}.
	 * @param identifier To set up
	 * @param identifies the resources needed, used to flag {@link TestIndexOptions#addCoreAlias(String, String)}
	 * @return the core, ready for indexing
	 */
	SolrServer getCore(String identifier);
	
	String getCoreUrl(String identifier);	
	
	void destroy();
	
}
