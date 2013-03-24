package se.repos.indexing;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import se.simonsoft.cms.testing.svn.CmsTestRepository;
import se.simonsoft.cms.testing.svn.SvnTestSetup;

public class NamedPipeTest {

	@After
	public void tearDown() {
		SvnTestSetup.getInstance().tearDown();
	}
	
	// http://stackoverflow.com/questions/3809022/most-efficient-way-to-communicate-from-shell-script-running-java-app
	// http://stackoverflow.com/questions/1666815/is-there-a-cross-platform-way-of-handling-named-pipes-in-java-or-should-i-write/1666925#1666925
	@Test public void pipe() throws IOException, InterruptedException {
	    Runtime.getRuntime().exec("mkfifo mypipe");

	    final String[] read = new String[1];
	    Thread t = new Thread() {
	        @Override
	        public void run() {
	            try {
	                BufferedReader r = new BufferedReader(new FileReader("mypipe"));
	                read[0] = r.readLine();
	            } catch (IOException e) {
	            }
	        }
	    };
	    t.start();

	    FileWriter w = new FileWriter("mypipe");
	    w.write("hello\n");
	    w.flush();
	    t.join();

	    assertEquals("hello", read[0]);
	}
	
	@Test
	public void testSvnHookToJava() {
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
		final File pipe = new File(hooksdir, "post-commit-pipe");
		try {
			Runtime.getRuntime().exec("mkfifo " + pipe.getAbsolutePath());
		} catch (IOException e) {
			fail(e.getMessage());
		}
		
		try {
			// hook that writes revision number to named pipe
			FileWriter topipe = new FileWriter(postCommitSh);
			topipe.write("#!/bin/sh\n");
			topipe.write("echo $2 > " + pipe.getAbsolutePath() + "\n");
			topipe.close();
		} catch (IOException e) {
			fail(e.getMessage());
		}
		// note that the hook should block until java has read the message
		
		// set up receiver
	    final List<Long> revs = new LinkedList<Long>();
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					BufferedReader r = new BufferedReader(new FileReader(pipe));
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
				}
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
	    assertTrue("The actual commont shouldn't take long, got " + timeCommit, timeCommit < 1500);
	}
	
}
