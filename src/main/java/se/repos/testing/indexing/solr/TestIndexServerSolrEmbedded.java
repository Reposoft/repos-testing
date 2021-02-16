/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.solr;

import java.io.File;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;

import se.repos.testing.indexing.TestIndexOptions;
import se.repos.testing.indexing.TestIndexServer;

public class TestIndexServerSolrEmbedded extends TestIndexServerSolrHome
		implements TestIndexServer {

	private File instanceDir = null;
	
	private CoreContainer container = null;
	
	@Override
	public void beforeTest(TestIndexOptions options) {
		if (instanceDir != null) {
			// would be very easy to reuse if options are identical
			throw new IllegalStateException("Test server reuse with new config is currently not supported. Must run destroy() after each test.");
		}
		this.instanceDir = super.createHomeWithCores(options);
		createServer();
	}

	@Override
	public SolrClient getCore(String identifier) {
		SolrClient core = new EmbeddedSolrServer(container, identifier);
		return core;
	}

	@Override
	public String getCoreUrl(String identifier) {
		throw new UnsupportedOperationException("Embedded Solr server does not support http calls, use Example server instead");
	}
	
	@Override
	public void destroy() {
		// We've had issues with test teardowns failing to delete the instanceDir
		//If this shutdown doesn't help we should rewrite getCore to keep a reference to the cores (one per identifier) and shut down those too
		if (container != null) {
			container.shutdown();
		}
		// this is what has failed with java.io.IOException: Unable to delete directory /tmp/testindexing-4294212186812628888.dir/repositem/data/index.
		super.destroy(instanceDir);
		instanceDir = null;
	}
	
	protected void createServer() {
		container = CoreContainer.createAndLoad(instanceDir.toPath());
		container.load();
	}
	

}
