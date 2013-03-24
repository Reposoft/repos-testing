package se.repos.indexing.testing.svn;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.apache.solr.client.solrj.SolrQuery;
import org.junit.After;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import se.repos.search.SearchReposItem;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

public class SvnTestIndexingTest {

	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
	}	
	
	@Test
	public void testEnableCmsTestRepository() {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		// this should be all that is needed for the default setup
		SearchReposItem search = SvnTestIndexing.getInstance().enable(repo);
		
		// dump file contents should be indexed
		Iterable<CmsItem> result = search.query(new SolrQuery("type:folder"));
		assertEquals("should index initial contents", "dir", result.iterator().next().getId().getRelPath().getName());
		
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
		
		
	}
	
	@Test
	public void testWithAdditionalIndexers() {
		
	}

}
