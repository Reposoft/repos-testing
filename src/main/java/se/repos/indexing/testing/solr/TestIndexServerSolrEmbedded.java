package se.repos.indexing.testing.solr;

import java.io.File;
import java.io.IOException;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;

import se.repos.indexing.testing.TestIndexOptions;
import se.repos.indexing.testing.TestIndexServer;

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
	public SolrServer getCore(String identifier) {
		SolrServer core = new EmbeddedSolrServer(container, identifier);
		return core;
	}

	@Override
	public void destroy() {
		super.destroy(instanceDir);
		instanceDir = null;
	}
	
	protected void createServer() {
		container = new CoreContainer(instanceDir.getAbsolutePath());
		container.load();
	}
	
	private void initExperiments() {
		// Everything below is a big mess because we're investigating solr instantiation		
		
		// Trying to figure out how to load a solr core.
		//  - No manual copying or linking of core
		//  - Probably needs to support multiple cores some day
		//  - Tests run from modules that depend on repos-indexing as jar
		
		// http://lucene.apache.org/solr/4_4_0/solr-core/index.html?org/apache/solr/client/solrj/embedded/EmbeddedSolrServer.html
		// http://lucene.apache.org/solr/4_4_0/solr-core/org/apache/solr/core/CoreContainer.html
		// http://lucene.apache.org/solr/4_4_0/solr-core/org/apache/solr/core/SolrResourceLoader.html
		File instanceDir;
		try {
			instanceDir = File.createTempFile("testindexing-", ".dir");
		} catch (IOException e) {
			throw new RuntimeException("not handled", e);
		}
		instanceDir.delete();
		instanceDir.mkdir();
		System.out.println("Instance dir is " + instanceDir);
		SolrResourceLoader resourceLoader = new SolrResourceLoader(instanceDir.getAbsolutePath());
		CoreContainer coreContainer = new CoreContainer(resourceLoader);
		
		// from our SolrTestCaseJ4 concept
		String solrhome = "se/repos/indexing/solr";
		String config = solrhome + "/repositem/conf/solrconfig.xml";
		String schema = solrhome + "/repositem/conf/schema.xml";
		String testSolrHome = "src/test/resources/" + solrhome; // has to be in classpath because "collection1" is hardcoded in TestHarness initCore/createCore

		// https://code.google.com/p/gbif-occurrencestore/source/browse/occurrence/trunk/occurrence-index-builder/src/main/java/org/gbif/occurrence/index/hadoop/EmbeddedSolrServerBuilder.java
		CoreDescriptor repositemDescriptor = new CoreDescriptor(coreContainer, /*props*/ null);
		SolrCore repositem = new SolrCore("repositem", repositemDescriptor);
		EmbeddedSolrServer solrServer = new EmbeddedSolrServer(coreContainer, "-multicore-");
		
		// TestHarness used in SolrTestCaseJ4 has been rewritten in 4.4.0
		// http://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_4_4_0/solr/test-framework/src/java/org/apache/solr/util/TestHarness.java
		// Now uses SolrConfig solrConfig, IndexSchema indexSchema - though only to do getResourceName()
		
		// http://grokbase.com/t/lucene/solr-user/10am1t4xff/embeddedsolrserver-with-one-core-and-schema-xml-loaded-via-classloader-is-it-possible		
	}

}
