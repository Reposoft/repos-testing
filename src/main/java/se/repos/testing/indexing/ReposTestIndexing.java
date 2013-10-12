/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.solr.client.solrj.SolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.testing.ReposTestBackend;
import se.repos.testing.cmstest.ReposTestBackendCmsTestingSvn;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.testing.svn.CmsTestRepository;


public class ReposTestIndexing {
	
	private static final Logger logger = LoggerFactory.getLogger(ReposTestIndexing.class);

	private static ReposTestIndexing instance = null;
	
	/**
	 * State in current test, not thread safe of course.
	 */
	private TestIndexOptions options = null;
	private Collection<Thread> threads = new LinkedList<Thread>();

	private TestIndexServer server;
	
	/**
	 * Enforce singleton, makes optimizations possible.
	 */
	private ReposTestIndexing() {
	}
	
	/**
	 * Instead of constructing the object in each test run, this allows reuse of solr setup.
	 * The instances are not threadsafe however, so tests should run in sequence.
	 * @return
	 */
	public static ReposTestIndexing getInstance() {
		return getInstance(new TestIndexOptions().itemDefaults());
	}
	
	public static ReposTestIndexing getInstance(TestIndexOptions options) {
		if (instance == null) {
			instance = new ReposTestIndexing();
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
	
	public String getCoreUrl(String identifier) {
		return this.server.getCoreUrl(identifier);
	}
	
	/**
	 * Enable blocking hook call from a test repository,
	 * using index handlers from {@link #getInstance(TestIndexOptions)}.
	 * @param repo A repository
	 * @return for chaining, suggest
	 */
	public ReposTestIndexing enable(CmsTestRepository repo) {
		ReposTestBackend backend = new ReposTestBackendCmsTestingSvn(repo);
		return this.enable(backend);
	}
	
	public ReposTestIndexing enable(ReposTestBackend backend) {
		return enable(backend, Guice.createInjector());
	}
	
	public ReposTestIndexing enable(ReposTestBackend backend, Injector parent) {
		Module backendConfig = backend.getConfiguration();
		Module config = options.getConfiguration(getCore("repositem"));
		Injector context = parent.createChildInjector(backendConfig, config);
		
		context.getInstance(IndexingSchedule.class).start();
		
		ReposIndexing indexing = context.getInstance(ReposIndexing.class);
		
		ReposTestBackend.HookInvocation postcommit = new ReposIndexingInvocation(indexing);
		backend.activate(context, postcommit);
		
		return this;
	}
	
	private class ReposIndexingInvocation implements ReposTestBackend.HookInvocation {
		
		private ReposIndexing indexing;

		ReposIndexingInvocation(ReposIndexing indexing) {
			this.indexing = indexing;
		}

		@Override
		public void postCommit(CmsRepository repository, RepoRevision revision) {
			this.indexing.sync(revision);
		}

		@Override
		public void hasPreloaded(CmsRepository repository, RepoRevision revision) {
			this.indexing.sync(revision);
		}
		
	}	
	
}
