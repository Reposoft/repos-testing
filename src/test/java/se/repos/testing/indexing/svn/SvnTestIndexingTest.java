/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.svn;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnImport;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import se.repos.testing.indexing.TestIndexOptions;
import se.repos.testing.indexing.svn.SvnTestIndexing;
import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

public class SvnTestIndexingTest {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
		SvnTestIndexing.getInstance().tearDown(); // TODO make static and set up + tear down only once?
	}
	
	@Test(timeout=100000)
	public void testEnableCmsTestRepository() throws SolrServerException {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		// single core init with default configuration
		logger.debug("Enabling indexing for {}", repo);
		SolrServer solr = SvnTestIndexing.getInstance().enable(repo).getCore("repositem");
		
		// TODO initial indexing to HEAD needed, or do we always get commits in tests? - no we don't
		//QueryResponse result = solr.query(new SolrQuery("type:folder"));
		//assertTrue("should have indexed something, got " + result.getResults().getNumFound() + " results", result.getResults().size() > 0);
		//assertEquals("should index initial contents", "dir", result.getResults().iterator().next().getFieldValue("pathname"));
		
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
		
		QueryResponse r2 = solr.query(new SolrQuery("path:\"/dir2\""));
		assertEquals("should have indexed upon commit and blocked until indexing is done", 1, r2.getResults().getNumFound());
		
		// test that the hook+thread concept survives another commit
		try {
			SvnRemoteCopy cp = op.createRemoteCopy();
			cp.setCommitMessage("copy dir 2");
			cp.addCopySource(SvnCopySource.create(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/dir2")), SVNRevision.HEAD));
			cp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/dir3")));
			SVNCommitInfo c2 = cp.run();
			assertEquals("should commit another", 3, c2.getNewRevision());
		} catch (SVNException e) {
			throw new RuntimeException(e);
		}
		
		QueryResponse r3 = solr.query(new SolrQuery("path:\"/dir3\""));
		assertEquals("should have indexed upon commit and blocked until indexing is done", 1, r3.getResults().getNumFound());
	}
	
	@Test
	public void testWithAdditionalIndexers() {
		
	}
	
	// Tests the technology, not our implementation
	// http://stackoverflow.com/questions/3809022/most-efficient-way-to-communicate-from-shell-script-running-java-app
	// http://stackoverflow.com/questions/1666815/is-there-a-cross-platform-way-of-handling-named-pipes-in-java-or-should-i-write/1666925#1666925	
	@Test(timeout=10000)
	public void testNamedPipe() {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		File hooksdir = new File(repo.getAdminPath(), "hooks");
		assertTrue(hooksdir.exists());
		assertTrue(hooksdir.canWrite());
		File postCommitSh = new File(hooksdir, "post-commit");
		try {
			postCommitSh.createNewFile();
		} catch (IOException e) {
			fail(e.getMessage());
		}
		postCommitSh.setExecutable(true);
		
		// set up named pipe
		final File pipe = new File(hooksdir, "post-commit.pipe.test");
		try {
			Runtime.getRuntime().exec("mkfifo " + pipe.getAbsolutePath());
		} catch (IOException e) {
			fail(e.getMessage());
		}
		
		try {
			// hook that writes revision number to named pipe
			FileWriter hookbridge = new FileWriter(postCommitSh);
			hookbridge.write("#!/bin/sh\n");
			hookbridge.write("echo $2 > " + pipe.getAbsolutePath() + "\n");
			hookbridge.write("cat " + pipe.getAbsolutePath() + " >&2\n");			
			hookbridge.close();
		} catch (IOException e) {
			fail(e.getMessage());
		}
		// note that the hook should block until java has read the message
		
		// set up receiver
	    final List<Long> revs = new LinkedList<Long>();
		Thread t = new Thread() {
			@Override
			public void run() {
				System.out.println("Awaiting hook call at " + pipe);
				BufferedReader r = null;
				try {
					r = new BufferedReader(new FileReader(pipe));
					// TODO make reading take 1000 ms so we can verify that the svn operation blocks
					String echoed = r.readLine();
					revs.add(Long.parseLong(echoed));
					// we'd have to run indexing of the revision here, and make
					// all of it sync
					// have different test setups for indexing fast and indexing
					// slow (most tests won't need fulltext, thumbs, xml)
					// TODO when to close reader during a test?
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (r != null) {
						try {
							r.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				System.out.println("Now we do a quite slow indexing here and the commit should not return until we're done");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				System.out.println("Writing response to waiting (hopefully) hook");
				BufferedWriter w = null;
				try {
					w = new BufferedWriter(new FileWriter(pipe));
					w.write("Test indexing completed revision " + revs.get(revs.size()-1) + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (w != null) {
						try {
							w.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				System.out.println("Hook completed.");
			}
		};
	    t.start();
		
		// now do an operation
		SvnOperationFactory op = new SvnOperationFactory();
		op.setAuthenticationManager(repo.getSvnkit().getAuthenticationManager());
		SvnRemoteDelete del = op.createRemoteDelete();
		del.setCommitMessage("Deleting file");
		try {
			del.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/t1.txt")));
		} catch (SVNException e) {
			fail("svnkit's api's make some very easy things very difficult. " + e.getMessage());
		}
		long timeBeforeCommit = System.currentTimeMillis();
		System.out.println("Running commit to " + repo.getUrl());
		SVNCommitInfo commitInfo = null;
		try {
			commitInfo = del.run();
		} catch (SVNException e) {
			fail("Operation on test repo failed. " + e.getMessage());
		}
		assertEquals("should have deleted", 2, commitInfo.getNewRevision());
		long timeCommit = System.currentTimeMillis() - timeBeforeCommit;
		
		// quit pipe receiver
	    try {
			t.join();
		} catch (InterruptedException e) {
			fail("threading stuff. " + e.getMessage());
		}

	    // verify that hook revision has been received
	    assertEquals("Should have got the revision from the hook", 2, revs.get(0).intValue());
	    assertTrue("The commit should block while the indexing operation runs, got " + timeCommit, timeCommit > 1000);
	    assertTrue("The actual commont shouldn't take long, got " + timeCommit, timeCommit < 10000);
	}
	
	/**
	 * Catch 22: Tests that use additional cores need the SolrServer instance for dependency injection into services,
	 * but don't have an instance of SvnTestIndexing until they can create their handler - also dependency injection.
	 * @throws Exception 
	 */
	@Test(timeout=100000)
	public void testCustomCore() throws SolrServerException, Exception {
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository();
		
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		options.addCore("dummycore", "se/repos/indexing/testing/solr/dummycore/**");
		SolrServer coreNotCreatedYet = null; // TODO how to get hold of dummycore here?
		options.addHandler(new DummyItemHandler(coreNotCreatedYet));
		
		SvnTestIndexing instance = SvnTestIndexing.getInstance(options);
		instance.enable(repo);
		
		SvnImport im = repo.getSvnkitOp().createImport();
		File tmp = File.createTempFile("temp-" + this.getClass().getName(), "");
		im.setSource(tmp);
		im.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl() + "/temp")));
		im.run();
		tmp.delete();
		
		assertEquals("Should have indexed through the promised core and the given handler", 1, 
				coreNotCreatedYet.query(new SolrQuery("*:*")).getResults().getNumFound());
		assertEquals("Core by name should be the same", 1,
				instance.getCore("dummycore").query(new SolrQuery("*:*")).getResults().getNumFound());
	}

}
