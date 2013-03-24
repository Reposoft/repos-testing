package se.repos.indexing;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Test;

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
		
		File pipe = new File(hooksdir, "post-commit-pipe");
		try {
			Runtime.getRuntime().exec("mkfifo " + pipe.getAbsolutePath());
		} catch (IOException e) {
			fail(e.getMessage());
		}
		
		try {
			FileWriter topipe = new FileWriter(postCommitSh);
			topipe.write("#!/bin/sh\n");
			topipe.write("echo $1 $2 > " + pipe.getAbsolutePath() + "\n");
			topipe.close();
		} catch (IOException e) {
			fail(e.getMessage());
		}
		// note that the hook should block until java has read the message
		
		
	}
	
}
