package se.repos.indexing;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import se.repos.indexing.testconfig.IndexingTestModule;
import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Verify features of the actual index, without using our abstractions.
 * This test should match stuff that we rely on for direct queries to solr from various places.
 * Create new test methods per integration case.
 * 
 * Using solr test-framework instead of http://wiki.apache.org/solr/Solrj#EmbeddedSolrServer-1
 * to get their temp file management etc.
 * 
 * http://lucene.apache.org/solr/4_2_0/solr-test-framework/org/apache/solr/SolrTestCaseJ4.html
 * http://blog.florian-hopf.de/2012/06/running-and-testing-solr-with-gradle.html
 * 
 * Lucene test framework requires "assertions"
 *  - http://docs.oracle.com/javase/1.4.2/docs/guide/lang/assert.html#enable-disable
 *  - http://java.sun.com/developer/technicalArticles/JavaLP/assertions/
 *  - http://stackoverflow.com/questions/5509082/eclipse-enable-assertions
 *  - http://maven.apache.org/plugins/maven-surefire-plugin/test-mojo.html#enableAssertions
 *  - Eclipse > Preferences > Java > Junit > Append -ea to JVM arguments ...
 */
public class ReposIndexingIntegrationTest extends SolrTestCaseJ4 {

	public static String solrhome = "se/repos/indexing/solr";

	@BeforeClass
	public static void beforeTests() throws Exception {
		try {
			SolrTestCaseJ4.initCore(solrhome + "/repositem/conf/solrconfig.xml", solrhome + "/repositem/conf/schema.xml",
					"src/test/resources/" + solrhome); // has to be in classpath because "collection1" is hardcoded in TestHarness initCore/createCore
		} catch (Exception e) {
			System.out.println("getSolrConfigFile()=" + getSolrConfigFile());
			System.out.println("testSolrHome=" + testSolrHome);
			throw e;
		}
	}
	
	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
	}
	
	/**
	 * @return instance for injection when integration testing our logic with solr, for index testing we do fine with SolrTestCaseJ4 helper methods
	 */
	public SolrServer getTestServer() {
		return new EmbeddedSolrServer(h.getCoreContainer(), h.getCore().getName()); // cache between tests?
	}
	
	public ReposIndexing getIndexing() {
		assertQ("index should be empty on each test run", req("*:*"), "//result[@numFound='0']");
		SolrServer solr = getTestServer();
		Injector injector = Guice.createInjector(new IndexingTestModule(solr));
		return injector.getInstance(ReposIndexing.class);
	}
	
	@Test
	public void testBasicSingleRevisionRepo() {
		ReposIndexing indexing = getIndexing();
		assertEquals("Should report null as last complete revision when the index is empty", null, indexing.getRevComplete());
		
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		repo.setKeep(true);
	}

}
