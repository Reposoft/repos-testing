/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import javax.inject.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.io.SVNRepository;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import se.repos.indexing.ReposIndexing;
import se.repos.search.SearchReposItem;
import se.repos.testing.ReposTestBackend;
import se.simonsoft.cms.backend.svnkit.info.CmsRepositoryLookupSvnkit;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;
import se.simonsoft.cms.testing.svn.CmsTestRepository;


public class SvnTestIndexing {
	
	private static final Logger logger = LoggerFactory.getLogger(SvnTestIndexing.class);

	// test may run as different user than svn so we might need to override umask
	private static final String MKFIFO_OPTIONS = " --mode=0666";

	private static SvnTestIndexing instance = null;
	
	/**
	 * State in current test, not thread safe of course.
	 */
	private TestIndexOptions options = null;
	private Collection<Thread> threads = new LinkedList<Thread>();

	private TestIndexServer server;
	
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
			instance.beforeTest(options); // we assume that getInstance is called before each test	
		}
		return instance;
	}
	
	protected void beforeTest(TestIndexOptions options) {
		if (!options.hasCore("repositem")) {
			throw new IllegalArgumentException("repositem core is required for indexing, use options.itemDefaults to configure");
		}
		this.options = options;
		// If we want to start reusing severs here we could clear all cores if there is an existing instance
		this.server = options.selectTestServer();
		this.server.beforeTest(options);
	}
	
	/**
	 * Must be called so the instance is reset and can take new arguments
	 */
	public void tearDown() {
		// TODO add feature for keeping data after test, handle deleteSolrDataAtTearDown
		tearDownThreads();
		// repository and hook is removed by SvnTestSetup.tearDown
		this.options.onTearDown();
		this.options = null;
		this.server.destroy();
		this.server = null;
		instance = null;
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
	 * @return Search abstraction for basic queries in the "repositem" core.
	 */
	public SearchReposItem getItem() {
		throw new UnsupportedOperationException("not implemented"); // needs a SearchReposItem impl
	}
	
	/**
	 * Enable blocking hook call from a test repository,
	 * using index handlers from {@link #getInstance(TestIndexOptions)}.
	 * @param repo A repository
	 * @return for chaining, suggest
	 */
	public SvnTestIndexing enable(CmsTestRepository repo) {
		Module config = options.getConfiguration(getCore("repositem"));
		Injector context = getContext(config);
		ReposIndexing indexing = context.getInstance(ReposIndexing.class);
		
		PostCommitInvocation postcommit = new ReposIndexingInvocation(indexing);
		installHooks(repo, postcommit);
		
		syncHead(repo, indexing);
		
		return this;
	}
	
	public SvnTestIndexing enable(ReposTestBackend backend) {
		Module backendConfig = backend.getConfiguration();
		Module config = options.getConfiguration(getCore("repositem"));
		Injector context = getContext(backendConfig, config);
		ReposIndexing indexing = context.getInstance(ReposIndexing.class);
		
		PostCommitInvocation postcommit = new ReposIndexingInvocation(indexing);
		backend.activate(context, postcommit);
		
		return this;
	}
	
	protected Injector getContext(Module... configuration) {
		return Guice.createInjector(configuration);
	}
	
	void syncHead(final CmsTestRepository repository, ReposIndexing indexing) {
		CmsRepositoryLookup lookup = getRepositoryLookup(repository);
		RepoRevision head = lookup.getYoungest(repository);
		if (head.getNumber() > 0) {
			logger.debug("Repository's revision is {} at enable, running initial sync", head);
			indexing.sync(repository, head);
		}
	}

	protected CmsRepositoryLookupSvnkit getRepositoryLookup(final CmsRepository repository) {
		return new CmsRepositoryLookupSvnkit(new HashMap<CmsRepository, Provider<SVNRepository>>() {private static final long serialVersionUID = 1L;{
			put(repository, ((CmsTestRepository) repository).getSvnkitProvider());
		}});
	}
	
	private void installHooks(CmsRepositoryInspection repository, final PostCommitInvocation postcommit) {
		final CmsRepository hookRepository = repository;
		File repositoryLocalPath = repository.getAdminPath();
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
		postCommitSh.setExecutable(true, false);
		
		// Set up named pipe file for communication
		final File pipe = new File(hooksdir, "post-commit.pipe");
		createPipe(pipe);
		
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
				postcommit.postCommit(hookRepository, revision);
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

	protected void createPipe(final File pipe) {
		final String pipecmd = "mkfifo" + MKFIFO_OPTIONS;
		try {
			Process exec = Runtime.getRuntime().exec(pipecmd + " " + pipe.getAbsolutePath());
			InputStream err = exec.getErrorStream();
			IOUtils.copy(err, System.out);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create named pipe using command line " + pipecmd, e);
		}
	}
	
	/**
	 * Called in separate thread when a test repository got a commit.
	 */
	public interface PostCommitInvocation {
		
		void postCommit(CmsRepository repository, Long revisionNumber);
		
	}
	
	private class ReposIndexingInvocation implements PostCommitInvocation {
		
		private ReposIndexing indexing;

		ReposIndexingInvocation(ReposIndexing indexing) {
			this.indexing = indexing;
		}

		@Override
		public void postCommit(CmsRepository repository, Long revisionNumber) {
			// TODO get actual commit timestamp from backend
			Date fake = new Date();
			RepoRevision rr = new RepoRevision(revisionNumber, fake);
			this.indexing.sync(repository, rr);
		}
		
	}
	
}
