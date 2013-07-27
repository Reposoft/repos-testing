package se.repos.indexing.testing;

import org.apache.solr.client.solrj.SolrServer;

/**
 * It is quite likely that, as development of applications continue, we'll need a more full fledged server than that used in test cases.
 * Which is why we try to abstract the actual server set up.
 */
public interface TestIndexServer {

	/**
	 * Verify connection to server, if needed.
	 */
	void start();
	
	SolrServer clearCore(String coreName);
	
}
