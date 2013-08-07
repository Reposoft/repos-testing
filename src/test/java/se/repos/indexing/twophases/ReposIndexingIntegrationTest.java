package se.repos.indexing.twophases;

import static org.junit.Assert.*;

import java.io.IOException;
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
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import se.repos.indexing.ReposIndexing;
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
		// save solr contents
		printHits(new SolrQuery("*:*"));
		// tests have different repositories so let's see if they can use the same solr instance //solrTestServer = null;
		// clear data from this test
		getSolr().deleteByQuery("*:*");
	}
	
	private void printHits(SolrQuery q) throws SolrServerException {
		System.out.println("--- solr contents " + q.toString() + " ---");
		SolrDocumentList results = getSolr().query(q).getResults();
		if (results.size() == 0) {
			System.out.println("empty");
		}
		for (SolrDocument d : results) {
			for (String f : d.getFieldNames()) {
				String v = "" + d.get(f);
				System.out.print(", " + f + ": " + v);
			}
			System.out.println("");
		}
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
	public void testBasicSingleRevisionRepo() throws SolrServerException, IOException {
		//assertQ("index should be empty on each test run", req("*:*"), "//result[@numFound='0']");
		ReposIndexing indexing = getIndexing();
		
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		assertEquals("Should report null as last complete revision when the index is empty", null, indexing.getRevComplete(repo));
		
		indexing.sync(repo, new RepoRevision(1, new Date(1))); // 2012-09-27T12:05:34.040515Z
		assertNotNull("Should track indexing", indexing.getRevComplete(repo));
		assertEquals("should have indexed up to the given revision", 1, indexing.getRevComplete(repo).getNumber());
		

		QueryResponse r1 = getSolr().query(new SolrQuery("type:commit").addSort("rev", ORDER.asc));
		assertEquals("Rev 0 should have been indexed in addition to 1", 2, r1.getResults().size());
		assertEquals("Rev 0 should be marked as completed", true, r1.getResults().get(0).getFieldValue("complete"));
		
		// new indexing service, recover sync status
		ReposIndexing indexing2 = getIndexing();
		indexing2.sync(repo, new RepoRevision(1, new Date(1))); // polling now done at sync
		assertNotNull("New indexing should poll for indexed revision",
				indexing2.getRevComplete(repo));
		assertTrue("New indexing should poll for highest indexed revision", 
				indexing2.getRevComplete(repo).getNumber() == 1);
	
		// mess with the index to see how sync status is handled
		SolrInputDocument fake2 = new SolrInputDocument();
		String id2 = r1.getResults().get(1).getFieldValue("id").toString().replace("#1", "#2");
		fake2.setField("id", id2);
		fake2.setField("complete", true);
		getSolr().add(fake2);
		getSolr().commit();
		assertEquals("Service is not expected to handle cuncurrent indexing", 1, indexing2.getRevComplete(repo).getNumber());
		
		ReposIndexing indexing3 = getIndexing();
		indexing3.sync(repo, new RepoRevision(1, new Date(1))); // polling now done at sync
		assertEquals("New indexing service should not mistake aborted indexing as completed", 1, indexing3.getRevComplete(repo).getNumber());
		//not implemented//assertEquals("New indexing service should see that a revision has started but not completed", 2, indexing3.getRevProgress(repo).getNumber());
		
		try {
			indexing3.sync(repo, new RepoRevision(2, new Date(2)));
			fail("Should attempt to index rev 2 because it is marked as in progress and the new indexing instance does not know the state of that operation so it has to assume that it was aborted");
		} catch (Exception e) {
			// expected, there is no revision 2
		}
	}
	
	@Test
	public void testMarkItemHead() throws SolrServerException {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1r3.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		repo.setKeep(true);
		
		ReposIndexing indexing = getIndexing();
		indexing.sync(repo, new RepoRevision(1, new Date(1)));
		
		SolrDocumentList r1 = getSolr().query(new SolrQuery("id:*@1").setSort("path", ORDER.asc)).getResults();
		assertEquals(3, r1.size());
		assertEquals("/dir", r1.get(0).getFieldValue("path"));
		for (int i = 0; i < 3; i++) {
			assertEquals(true, r1.get(i).getFieldValue("head"));
		}
		
		indexing.sync(repo, new RepoRevision(2, new Date(2)));
		SolrDocumentList r2r1 = getSolr().query(new SolrQuery("id:*@1").setSort("path", ORDER.asc)).getResults();
		assertEquals("/dir " + r2r1.get(0), true, r2r1.get(0).getFieldValue("head"));
		assertEquals("/dir/t2.txt " + r2r1.get(1), true, r2r1.get(1).getFieldValue("head"));
		assertEquals("should have updated old /t1.txt" + r2r1.get(2), false, r2r1.get(2).getFieldValue("head"));
		SolrDocumentList r2 = getSolr().query(new SolrQuery("id:*@2").setSort("path", ORDER.asc)).getResults();
		assertEquals("next revision should be head, " + r2.get(0), true, r2.get(0).getFieldValue("head"));
		
		indexing.sync(repo, new RepoRevision(3, new Date(3)));
		// everything from r1 should now have been replaced with later versions
		SolrDocumentList r3r1 = getSolr().query(new SolrQuery("id:*@1").setSort("path", ORDER.asc)).getResults();
		
		assertEquals("/dir", r3r1.get(0).get("path"));
		assertEquals("/dir/t2.txt", r3r1.get(1).get("path"));
		assertEquals("/t1.txt", r3r1.get(2).get("path"));

		assertEquals("Old file that hasn't been change should still be head", true, r3r1.get(1).getFieldValue("head"));
		assertEquals("The file that was changed in r3 should now be marked as non-head", false, r3r1.get(2).getFieldValue("head"));
		// TODO to assert on dir we need to first edit a file in it and verify it is still head (it should be, right?),
		// then edit a property on it to check that it's marked not head
		
		// TODO test for gaps, moves etc
		fail("Current solution is still fake, only works for previous revision");
	}

}
