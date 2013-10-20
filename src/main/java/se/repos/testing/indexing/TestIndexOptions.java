/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServer;

import com.google.inject.Module;

import se.repos.indexing.IndexingItemHandler;
import se.repos.lgr.Lgr;
import se.repos.lgr.LgrFactory;
import se.repos.testing.indexing.config.TestIndexDefaultModule;
import se.repos.testing.indexing.config.TestIndexHandlersModuleWithExtraInstances;
import se.repos.testing.indexing.config.TestIndexSolrCoreModule;
import se.repos.testing.indexing.solr.TestIndexServerSolrEmbedded;
import se.repos.testing.indexing.solr.TestIndexServerSolrJettyExample;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

/**
 * Carries no state, can be reused between tests if desired.
 */
public class TestIndexOptions {
	
	private static final Lgr logger = LgrFactory.getLogger();
	
	private Set<IndexingItemHandler> handlers = new LinkedHashSet<IndexingItemHandler>();

	private boolean itemDefaultHandlers = false;
	
	private Map<String, String> cores = new HashMap<String, String>();
	
	private Map<String, String> aliases = new HashMap<String, String>();
	
	// usage is a bit tricky so try to produce meaningful errors
	boolean coresUsed = false;
	boolean handlersUsed = false;
	
	/**
	 * Set up for basic "repositem" blocking indexing, i.e. structure but not contents.
	 * @return for chaining
	 */
	public TestIndexOptions itemDefaults() {
		this.addCore("repositem", "se/repos/indexing/solr/repositem/**");
		itemDefaultHandlers();
		return this;
	}

	protected void itemDefaultHandlers() {
		itemDefaultHandlers = true; // we don't configure handlers here anymore because the chain requires a config module
	}
	
	public TestIndexOptions addCore(String identifier, String resourcePattern) {
		if (coresUsed) {
			throw new IllegalStateException("Test indexing has already been initialized with cores, can not add new");
		}
		cores.put(identifier, resourcePattern);
		return this;
	}
	
	public boolean hasCore(String identifier) {
		return cores.containsKey(identifier);
	}
	
	/**
	 * Can be used by framework during configuration if the core needs to be stored under a different name.
	 * We're not sure yet how this works with solr.properties though.
	 * @param identifier
	 * @param actualCoreNameInSolr
	 */
	public void addCoreAlias(String identifier, String actualCoreNameInSolr) {
		aliases.put(identifier, actualCoreNameInSolr);
	}
	
	public String getCoreAlias(String identifier) {
		return aliases.get(identifier);
	}
	
	public boolean hasCoreAliases() {
		return aliases.size() > 0;
	}
	
	public boolean hasCoreAlias(String identifier) {
		return aliases.containsKey(identifier);
	}
	
	public Map<String, String> getCores() {
		coresUsed = true;
		return cores;
	}
	
	/**
	 * Must be done before {@link ReposTestIndexing#enable(CmsTestRepository)}
	 * @param handler Configured handler
	 * @return
	 */
	public TestIndexOptions addHandler(IndexingItemHandler handler) {
		if (handlersUsed) {
			throw new IllegalStateException("Test indexing has already been enabled with handlers, can not add new");
		}
		this.handlers.add(handler);
		return this;
	}
	
	/**
	 * Recommended custom config is to use modules, but prevonficured instances with @Inject can be added this way.
	 * 
	 * Note that handlers can't have dependencies to these, because the class will be different (unless we use cglib strategies etc).
	 * 
	 * @param handler To be wrapped
	 * @return from {@link #addHandler(IndexingItemHandler)}
	 */
	public TestIndexOptions addHandlerNodeps(IndexingItemHandler handler) {
		IndexingItemHandler wrapped;
		if (handler instanceof se.repos.indexing.Marker) {
			wrapped = new MarkerNodeps((se.repos.indexing.Marker) handler);
		} else {
			wrapped = new HandlerNodeps(handler);
		}
		logger.debug("Wrapping handler {} as {} to avoid dependency injection attempts", handler, wrapped);
		return addHandler(wrapped);
	}
	
	public Set<IndexingItemHandler> getHandlers() {
		handlersUsed = true;
		return handlers;
	}
	
	/**
	 * @param repositem from {@link ReposTestIndexing#enable(CmsTestRepository)}
	 * @return
	 */
	protected Collection<Module> getConfiguration(SolrServer repositem) {
		Collection<Module> config = getConfiguration();
		config.add(new TestIndexSolrCoreModule("repositem", repositem));
		return config;
	}

	/**
	 * Produces indexing configuration.
	 * 
	 * This configuration runs in addition to that from {@link se.repos.testing.ReposTestBackend#getConfiguration()}.
	 * 
	 * @return collection that supports add (of backend module etc)
	 */
	protected Collection<Module> getConfiguration() {
		if (!itemDefaultHandlers) {
			throw new IllegalStateException("Test indexing default config requires default handlers added, call itemDefaults() first.");
		}
		List<Module> config = new LinkedList<Module>();
		config.add(new TestIndexDefaultModule());
		config.add(new TestIndexHandlersModuleWithExtraInstances(handlers));
		return config;
	}

	/**
	 * Notified about SvnTestIndexing tearDown.
	 */
	public void onTearDown() {
		coresUsed = false;
		handlersUsed = false;
	}

	/**
	 * Called once per test to get the choice of server.
	 * @return server waiting for a call to {@link TestIndexServer#beforeTest(TestIndexOptions)}
	 */
	public TestIndexServer selectTestServer() {
		TestIndexServer test = TestIndexServerSolrJettyExample.locate();
		if (test != null) {
			return test;
		}
		logger.info("Embedded Solr used as fallback. Inspection of index won't be possible.");
		// fall back to embedded server
		return new TestIndexServerSolrEmbedded();
	}
	
}
