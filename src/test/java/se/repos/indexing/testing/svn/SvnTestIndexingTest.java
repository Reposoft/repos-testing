package se.repos.indexing.testing.svn;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.After;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

public class SvnTestIndexingTest {

	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
	}
	
	@Test
	public void testEnableCmsTestRepository() throws SolrServerException {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		// this should be all that is needed for the default setup
		//SearchReposItem search = SvnTestIndexing.getInstance().enable(repo);
		//Iterable<CmsItem> result = search.query(new SolrQuery("type:folder"));
		// dump file contents should be indexed
		//assertEquals("should index initial contents", "dir", result.iterator().next().getId().getRelPath().getName());
		
		// TODO the API must support multiple cores, repositem and reposxml for example
		// TODO join between them?
		
		SolrServer solr = SvnTestIndexing.getInstance().enable(repo).getCore("repositem");
		
		QueryResponse result = solr.query(new SolrQuery("type:folder"));
		assertEquals("should index initial contents", "dir", result.getResults().iterator().next().getFieldValue("pathname"));
		
		// first commit
		SvnOperationFactory op = new SvnOperationFactory();
		op.setAuthenticationManager(repo.getSvnkit().getAuthenticationManager());
		try {
			SvnRemoteCopy cp = op.createRemoteCopy();
			cp.setCommitMessage("copy dir");
			cp.addCopySource(SvnCopySource.create(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/dir")), SVNRevision.HEAD));
			cp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/dir2")));
			SVNCommitInfo c2 = cp.run();
			assertEquals("should commit", 2, c2.getNewRevision());
		} catch (SVNException e) {
			throw new RuntimeException(e);
		}
		
		QueryResponse r2 = solr.query(new SolrQuery("path:/dir2"));
		assertEquals("should have indexed upon commit and blocked until indexing is done", 1, r2.getResults().getNumFound());
	}
	
	@Test
	public void testWithAdditionalIndexers() {
		
	}

}
