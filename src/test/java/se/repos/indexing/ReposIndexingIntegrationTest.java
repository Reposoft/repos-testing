package se.repos.indexing;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.SortOrder;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import se.repos.indexing.testconfig.IndexingTestModule;
import se.simonsoft.cms.item.RepoRevision;
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
	
	private SolrServer solrTestServer = null;

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
	public void tearDown() throws Exception {
		SvnTestSetup.getInstance().tearDown();
		super.tearDown();
		// tests have different repositories so let's see if they can use the same solr instance //solrTestServer = null;
	}
	
	/**
	 * @return instance for injection when integration testing our logic with solr, for index testing we do fine with SolrTestCaseJ4 helper methods
	 */
	public SolrServer getSolr() {
		if (solrTestServer == null) {
			solrTestServer = new EmbeddedSolrServer(h.getCoreContainer(), h.getCore().getName());
		}
		return solrTestServer;
	}
	
	public ReposIndexing getIndexing() {
		SolrServer solr = getSolr();
		Injector injector = Guice.createInjector(new IndexingTestModule(solr));
		return injector.getInstance(ReposIndexing.class);
	}
	
	@Test
	public void testBasicSingleRevisionRepo() throws SolrServerException {
		assertQ("index should be empty on each test run", req("*:*"), "//result[@numFound='0']");
		ReposIndexing indexing = getIndexing();
		
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		assertEquals("Should report null as last complete revision when the index is empty", null, indexing.getRevComplete(repo));
		
		indexing.sync(repo, new RepoRevision(1, new Date(1))); // 2012-09-27T12:05:34.040515Z
		assertNotNull("Should track indexing", indexing.getRevComplete(repo));
		assertEquals("should have indexed up to the given revision", 1, indexing.getRevComplete(repo).getNumber());
		
		// new indexing service
		ReposIndexing indexing2 = getIndexing();
		assertTrue("New indexing instance can't know current highest revision until it has been given a repository", null == indexing2.getRevComplete(repo));
		// TODO shouldn't the service be per repository? How does that work when there are multiple hooks at the same time?
		
		QueryResponse r1 = getSolr().query(new SolrQuery("type:commit").addSort("rev", ORDER.asc));
		//assertEquals("Rev 0 should have been indexed in addition to 1", 2, r1.getResults().size());
		assertEquals("Rev 0 should be marked as completed", false, r1.getResults().get(0).getFieldValue("inprogress"));
		
	}

}
