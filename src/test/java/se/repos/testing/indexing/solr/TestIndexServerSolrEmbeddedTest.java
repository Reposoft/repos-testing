/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.solr;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;

import se.repos.testing.indexing.TestIndexOptions;
import se.repos.testing.indexing.TestIndexServer;
import se.repos.testing.indexing.solr.TestIndexServerSolrEmbedded;

public class TestIndexServerSolrEmbeddedTest {

	@Test
	public void test() throws SolrServerException, IOException {
		// initialize server for one or more test runs
		TestIndexServer server = new TestIndexServerSolrEmbedded();
		// configure server for a test case with specific options
		TestIndexOptions o = new TestIndexOptions().itemDefaults();
		server.beforeTest(o);
		// get a core for a test
		SolrClient core = server.getCore("repositem");
		assertNotNull("Should configure a solr connection", core);
		QueryResponse result = core.query(new SolrQuery("*:*"));
		assertNotNull("Should run queyr");
		assertEquals("Core should be empty", 0, result.getResults().getNumFound());
		SolrInputDocument minimal = new SolrInputDocument();
		minimal.addField("id", "test1");
		core.add(minimal);
		core.commit();
		assertEquals("Core should now have 1 doc", 1, core.query(new SolrQuery("*:*")).getResults().getNumFound());
		// get a core again... TODO should be made empty
		// TODO shouldn't clearing be the responsibility of SvnTestIndexing
		try {
			server.beforeTest(o);
			// if re-initializing the server becomes a bottleneck the layer above could clear cores instead of running beforeTests, or change the definition of the function
			fail("Should require destroy between tests so we don't get unintentional reuse of data");
		} catch (IllegalStateException e) {
			// expected
		}
		// after all tests
		server.destroy();
	}

}
