package se.repos.indexing.testing.svn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import org.apache.solr.client.solrj.SolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.testing.TestIndexOptions;
import se.repos.indexing.testing.TestIndexServer;
import se.repos.indexing.testing.solr.TestIndexServerSolrEmbedded;
import se.repos.search.SearchReposItem;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.testing.svn.CmsTestRepository;


public class SvnTestIndexing {
	
	private static final Logger logger = LoggerFactory.getLogger(SvnTestIndexing.class);

	private static SvnTestIndexing instance = null;
	
	/**
	 * State in current test, not thread safe of course.
	 */
	private TestIndexOptions options = null;
	private TestIndexServer server = null;
	private ReposIndexing indexing = null;
	private Collection<Thread> threads = new LinkedList<Thread>();
	
	/**
	 * Enforce singleton, makes optimizations possible.
	 */
	private SvnTestIndexing() {
	}
	
	/**
	 * Instead of constructing the object in each test run, this allows reuse of solr setup.
	 * The instances are not threadsafe however, so tests should run in sequence.
	 * @return
	 */
	public static SvnTestIndexing getInstance() {
		return getInstance(new TestIndexOptions().itemDefaults());
	}
	
	public static SvnTestIndexing getInstance(TestIndexOptions options) {
		if (instance == null) {
			instance = new SvnTestIndexing();
		}
		instance.beforeTest(options); // we assume that getInstance is called before each test
		return instance;
	}
	
	protected void beforeTest(TestIndexOptions options) {
		if (!options.hasCore("repositem")) {
			throw new IllegalArgumentException("repositem core is required for indexing, use options.itemDefaults to configure");
		}
		this.options = options;
		// If we want to start reusing severs hear we could clear all cores if there is an existing instance
		this.server = new TestIndexServerSolrEmbedded();
		this.server.beforeTest(options);
		this.indexing = options.getIndexing(server.getCore("repositem"));
	}
	
	/**
	 * @return Search abstraction for basic queries in the "repositem" core.
	 */
	public SearchReposItem getItem() {
		throw new UnsupportedOperationException("not implemented"); // needs a SearchReposItem impl
	}
	
	/**
	 * Loads a solr core that was configured and cleared at {@link #getInstance(TestIndexOptions)}.
	 * To get live index data in tests, first call {@link #enable(CmsTestRepository)}.
	 * @param identifier our internal core name, though maybe suffixed in Solr
	 * @return direct Solr access to the core
	 */
	public SolrServer getCore(String identifier) {
		if (!options.hasCore(identifier)) {
			throw new IllegalArgumentException("Core '" + identifier + "' not found in test cores " + options.getCores().keySet());
		}
		return this.server.getCore(identifier);
	}
	
	/**
	 * Enable blocking hook call from a test repository,
	 * using index handlers from {@link #getInstance(TestIndexOptions)}.
	 * @param repo A repository
	 * @return for chaining, suggest
	 */
	public SvnTestIndexing enable(CmsTestRepository repo) {
		PostCommitInvocation postcommit = new ReposIndexingInvocation(repo, indexing);
		installHooks(repo.getAdminPath(), postcommit);
		// TODO boolean deleteSolrDataAtTearDown = repo.isKeep();
		return this;
	}
	
	public void tearDown() {
		// TODO handle deleteSolrDataAtTearDown
		tearDownThreads();
		instance.server.destroy();
		// repository and hook is removed by SvnTestSetup.tearDown
		instance.options = null;
		instance.server = null;
		instance.indexing = null;
	}
	
	void tearDownThreads() {
		for (Thread t : threads) {
			t.interrupt();
			try {
				t.join(100);
			} catch (InterruptedException e) {
				logger.debug("Thread interrupt failed");
			}
		}
		threads.clear();
	}
	
	private void installHooks(File repositoryLocalPath, final PostCommitInvocation postcommit) {
		File hooksdir = new File(repositoryLocalPath, "hooks");
		if (!hooksdir.exists()) {
			throw new IllegalArgumentException("No hooks folder found in repository path " + repositoryLocalPath);
		}
		if (!hooksdir.canWrite()) {
			throw new IllegalArgumentException("Hooks folder not writable at " + hooksdir);
		}
		File postCommitSh = new File(hooksdir, "post-commit");
		try {
			postCommitSh.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to create post-commit script " + postCommitSh, e);
		}
		postCommitSh.setExecutable(true);
		
		// Set up named pipe file for communication
		final String pipecmd = "mkfifo";
		final File pipe = new File(hooksdir, "post-commit.pipe");
		try {
			Runtime.getRuntime().exec(pipecmd + " " + pipe.getAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to create named pipe using command line " + pipecmd, e);
		}
		
		// Set up hook that writes revision number to named pipe
		try {
			FileWriter hookbridge = new FileWriter(postCommitSh);
			hookbridge.write("#!/bin/sh\n");
			hookbridge.write("echo $2 > " + pipe.getAbsolutePath() + "\n");
			hookbridge.write("cat " + pipe.getAbsolutePath() + " >&2\n");			
			hookbridge.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write to post-commit hook " + postCommitSh, e);
		}
		
		// Listen to hook calls
		Thread t = new Thread() {
			@Override
			public void run() {
				while (true) {
					logger.info("Starting hook wait at {}", pipe);
					runRevision();
					logger.info("Hook wait loop ended for {}", pipe);
				}
			}
			
			void runRevision() {
				logger.trace("Awaiting next revision");
				Long revision = null;
				BufferedReader r = null;
				try {
					r = new BufferedReader(new FileReader(pipe));
					String echoed = r.readLine();
					try {
						revision = Long.parseLong(echoed);
					} catch (NumberFormatException e) {
						logger.error("Invalid revision number from hook, got '" + echoed + "'", e);
					}
				} catch (IOException e) {
					logger.error("Reading from hook pipe failed", e);
					throw new RuntimeException("Aborting because of hook communication error at reading", e);
				} finally {
					if (r != null) {
						try {
							r.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				postcommit.postCommit(revision);
				logger.trace("Hook handler complete, writing confirmation to {}", pipe);
				BufferedWriter w = null;
				try {
					w = new BufferedWriter(new FileWriter(pipe));
					w.write("Test indexing completed revision " + revision + "\n");
				} catch (IOException e) {
					logger.error("Writing to hook pipe failed", e);
					throw new RuntimeException("Aborting because of hook communication error at confirmation", e);
				} finally {
					if (w != null) {
						try {
							w.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				logger.debug("Revision {} hook completed for {}", revision, pipe);
			}
		};
	    t.start();
	}
	
	/**
	 * Called in separate thread when a test repository got a commit.
	 */
	private interface PostCommitInvocation {
		
		void postCommit(Long revision); 
		
	}
	
	/* TODO how does runtime webapp solve rev->timestamp mapping?
	private class RevisionDateProvider {
		
		public RevisionDateProvider(CmsTestRepository repository) {
			// currently a dummy impl without the actual date
		}
		
		RepoRevision getRevision(Long revisionNumber) {
			
		}
		
	}
	*/
	
	private class ReposIndexingInvocation implements PostCommitInvocation {
		
		private CmsTestRepository repository;
		private ReposIndexing indexing;

		ReposIndexingInvocation(CmsTestRepository repository, ReposIndexing indexing) {
			this.repository = repository;
			this.indexing = indexing;
		}

		@Override
		public void postCommit(Long revision) {
			// TODO get real revision date
			Date fake = new Date();
			RepoRevision rr = new RepoRevision(revision, fake);
			this.indexing.sync(repository, rr);
		}
		
	}
	
}
