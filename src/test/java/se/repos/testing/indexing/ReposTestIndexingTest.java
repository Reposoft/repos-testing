/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnRemoteMkDir;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.item.IndexingItemProgress;
import se.repos.testing.indexing.TestIndexOptions;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

public class ReposTestIndexingTest {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
		ReposTestIndexing.getInstance().tearDown();
	}
	
	@Test(timeout=100000)
	public void testEnableCmsTestRepository() throws SolrServerException, IOException {
		InputStream dumpfile = this.getClass().getClassLoader().getResourceAsStream(
				"se/repos/indexing/testrepo1.svndump");
		assertNotNull(dumpfile);
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository().load(dumpfile);
		
		// single core init with default configuration
		logger.debug("Enabling indexing for {}", repo);
		ReposTestIndexing indexing = ReposTestIndexing.getInstance().enable(repo);
		SolrClient solr = indexing.getCore("repositem");
		
		try {
			indexing.getContext().getInstance(CmsCommit.class); // didn't work in 0.7
		} catch (/*com.google.inject.Creation*/Exception e) {
			// we currenty don't bind user level services
		}
		
		// before any commits but after load+enable
		QueryResponse result = solr.query(new SolrQuery("type:folder AND pathname:dir"));
		assertTrue("should have indexed immediately upon enable, got " + result.getResults().getNumFound() + " results", result.getResults().size() > 0);
		
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
		
		QueryResponse r2 = solr.query(new SolrQuery("head:true AND path:\"/dir2\""));
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
	public void testWithAdditionalHandlers() {
		
	}
	
	/**
	 * Catch 22: Tests that use additional cores need the SolrServer instance for dependency injection into services,
	 * but don't have an instance of SvnTestIndexing until they can create their handler - also dependency injection.
	 * @throws Exception 
	 */
	@Test(timeout=100000)
	public void testCustomCore() throws SolrServerException, Exception {
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository();
		
		// first add cores
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		// define the core resource for export
		options.addCore("dummycore", "se/repos/indexing/testing/solr/dummycore/**");
		// then getInstance
		ReposTestIndexing indexing = ReposTestIndexing.getInstance(options);
		// then add handlers
		SolrClient extracore = indexing.getCore("dummycore");
		options.addHandler(new DummyItemHandler(extracore));
		// then enable for repository
		indexing.enable(repo);
		
		SvnRemoteMkDir mkdir = repo.getSvnkitOp().createRemoteMkDir();
		mkdir.addTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl()).appendPath("/dir", false)));
		mkdir.run();
		
		assertEquals("Should have indexed through the promised core and the given handler", 1, 
				extracore.query(new SolrQuery("*:*")).getResults().getNumFound());
		assertEquals("Core by name should be the same", 1,
				extracore.query(new SolrQuery("*:*")).getResults().getNumFound());
		assertEquals("Should have indexed with the default handlers in repositem", 1,
				indexing.getCore("repositem").query(new SolrQuery("head:true AND pathname:dir")).getResults().getNumFound());
	}

	@Test(timeout=100000)
	public void testSolrAddError() throws SolrServerException, Exception {
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository();
		
		// first add cores
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		options.addHandler(new IndexingItemHandler() {
			@Override
			public void handle(IndexingItemProgress progress) {
				progress.getFields().addField("some-unknown-field", "value");
			}
			@Override
			public Set<Class<? extends IndexingItemHandler>> getDependencies() {
				return null;
			}
		});
		
		ReposTestIndexing.getInstance(options).enable(repo);
		
		// any commit will now produce an invalid field to solr, should ideally throw exception here but most importantly shouldn't hang the test
		SvnRemoteMkDir mkdir = repo.getSvnkitOp().createRemoteMkDir();
		mkdir.addTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl()).appendPath("/dir", false)));
		mkdir.run();
	}
		
	@Test(timeout=100000)
	public void testCoreInClasspathNotFound() throws SolrServerException, Exception {
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository();
		
		// first add cores
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		options.addCore("dummycore", "se/repos/indexing/testing/solr/notfound/**");
		
		try {
			ReposTestIndexing.getInstance(options).enable(repo);
			fail("Should throw useful exception");
		} catch (Exception e) {
			assertEquals("Got " + e, "No resources found in extraction pattern se/repos/indexing/testing/solr/notfound/**", e.getMessage());
		}
	}

	@Ignore // should be fixed but is low priority
	@Test(expected=RuntimeException.class)
	public void testRecoverAfterException() throws Exception {
		CmsTestRepository repo = SvnTestSetup.getInstance().getRepository();
		
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		options.addHandler(new IndexingItemHandler() {
			@Override
			public void handle(IndexingItemProgress progress) {
				throw new RuntimeException("Some error here and the test should not hang");
			}
			@Override
			public Set<Class<? extends IndexingItemHandler>> getDependencies() {
				return new HashSet<Class<? extends IndexingItemHandler>>();
			}
		});
		
		ReposTestIndexing indexing = ReposTestIndexing.getInstance(options);
		indexing.enable(repo);
		
		SvnRemoteMkDir mkdir = repo.getSvnkitOp().createRemoteMkDir();
		mkdir.addTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(repo.getUrl()).appendPath("/dir", false)));
		mkdir.run();
	}
	
	@Test
	public void testMultipleRepositories() {
		// For now all repositories must have the same configuration (handlers etc) because of the way that ReposIndexing is initialized
		// Future functionality is one instance of ReposIndexing per repository, which should be quite easy to support
	}
	
	// Tests the technology, not our implementation
	// http://stackoverflow.com/questions/3809022/most-efficient-way-to-communicate-from-shell-script-running-java-app
	// http://stackoverflow.com/questions/1666815/is-there-a-cross-platform-way-of-handling-named-pipes-in-java-or-should-i-write/1666925#1666925	
	// Fails on build server while the tests of the actual impl pass//@Test(timeout=60000)
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
		postCommitSh.setExecutable(true, false);
		System.out.println("namedpipe: Created hook " + postCommitSh);
		
		// set up named pipe
		final File pipe = new File(hooksdir, "post-commit.pipe.test");
		try {
			Runtime.getRuntime().exec("mkfifo --mode=0666 " + pipe.getAbsolutePath());
		} catch (IOException e) {
			fail(e.getMessage());
		}
		System.out.println("namedpipe: Created pipe " + pipe);
		
		try {
			// hook that writes revision number to named pipe, then waits for confirmation that hook has processed
			FileWriter hookbridge = new FileWriter(postCommitSh);
			hookbridge.write("#!/bin/sh\n");
			hookbridge.write("echo $2 > " + pipe.getAbsolutePath() + "\n");
			hookbridge.write("cat " + pipe.getAbsolutePath() + " >&2\n");			
			hookbridge.close();
		} catch (IOException e) {
			fail(e.getMessage());
		}
		// note that the hook should block until java has read the message
		System.out.println("namedpipe: Wrote to hook " + postCommitSh);
		
		// set up receiver
	    final List<Long> revs = new LinkedList<Long>();
		Thread t = new Thread() {
			@Override
			public void run() {
				System.out.println("namedpipe-wait: Wait thread started");
				BufferedReader r = null;
				try {
					r = new BufferedReader(new FileReader(pipe));
					System.out.println("namedpipe-wait: Awaiting hook call at " + pipe);
					String echoed = r.readLine();
					System.out.println("namedpipe-wait: Got line from pipe =" + echoed);
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
				System.out.println("namedpipe-wait: Now we do a quite slow indexing here and the commit should not return until we're done");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				System.out.println("namedpipe-wait: Writing response to waiting (hopefully) hook");
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
				System.out.println("namedpipe-wait: Hook completed.");
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
		System.out.println("namedpipe: Running commit to " + repo.getUrl());
		SVNCommitInfo commitInfo = null;
		try {
			commitInfo = del.run();
		} catch (SVNException e) {
			fail("Operation on test repo failed. " + e.getMessage());
		}
		System.out.println("namedpipe: Commit returned revision " + commitInfo.getNewRevision());
		assertEquals("should have deleted", 2, commitInfo.getNewRevision());
		long timeCommit = System.currentTimeMillis() - timeBeforeCommit;
		
		// quit pipe receiver
	    try {
			t.join();
		} catch (InterruptedException e) {
			fail("threading stuff. " + e.getMessage());
		}
	    System.out.println("namedpipe: Pipe reader thread joined");

	    // verify that hook revision has been received
	    assertEquals("Should have got the revision from the hook", 2, revs.get(0).intValue());
	    assertTrue("The commit should block while the indexing operation runs, got " + timeCommit, timeCommit > 1000);
	    assertTrue("The actual commont shouldn't take long, got " + timeCommit, timeCommit < 10000);
	}	
	
}
