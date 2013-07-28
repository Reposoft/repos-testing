package se.repos.indexing.testing;

import org.apache.solr.client.solrj.SolrServer;

/**
 * It is quite likely that, as development of applications continue, we'll need a more full fledged server than that used in test cases.
 * Which is why we try to abstract the actual server set up.
 * 
 * Server should not ensure reuse, because that is up to the test framework.
 */
public interface TestIndexServer {

	/**
	 * Verify connection to server, if needed.
	 * Also used to reset server between runs, if supported.
	 */
	void beforeTest(TestIndexOptions options);
	
	/**
	 * Get a core for use in current test.
	 * @param identifier To set up
	 * @param identifies the resources needed, used to flag {@link TestIndexOptions#addCoreAlias(String, String)}
	 * @return the core, ready for indexing
	 */
	SolrServer clearCore(String identifier);
	
	void destroy();
	
}
