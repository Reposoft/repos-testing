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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;

import se.repos.indexing.IndexingItemHandler;
import se.repos.testing.indexing.config.TestIndexDefaultModule;
import se.repos.testing.indexing.config.TestIndexHandlersModuleWithExtraInstances;
import se.repos.testing.indexing.solr.TestIndexServerSolrEmbedded;
import se.repos.testing.indexing.solr.TestIndexServerSolrJettyExample;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

/**
 * Carries no state, can be reused between tests if desired.
 */
public class TestIndexOptions {
	
	private static final Logger logger = LoggerFactory.getLogger(TestIndexOptions.class);
	
	private List<Module> config = new LinkedList<Module>();
	
	private Set<IndexingItemHandler> handlers = new LinkedHashSet<IndexingItemHandler>(); // if used this is referenced from a Module in #config
	
	private Map<String, TestCore> cores = new HashMap<String, TestCore>();
	
	@Deprecated // extend TestCore to support this, when aliases are needed
	private Map<String, String> aliases = new HashMap<String, String>();
	
	// usage is a bit tricky so try to produce meaningful errors
	boolean coresUsed = false;
	boolean handlersUsed = false;
	
	/**
	 * Include the "repositem" core.
	 */
	public TestIndexOptions() {
		this.addCoreDefault();
	}
	
	/**
	 * Include custom cores, possibly without "repositem" (though indexing will not be useful without it).
	 */
	public TestIndexOptions(Iterable<TestCore> cores) {
		for (TestCore c : cores) {
			this.addCore(c);
		}
	}
	
	public TestIndexOptions addModule(Module module) {
		config.add(module);
		return this;
	}
	
	/**
	 * Set up for basic "repositem" blocking indexing, i.e. structure but not contents.
	 * 
	 * 
	 * 
	 * @return for chaining
	 */
	public TestIndexOptions itemDefaults() {
		itemDefaultServices();
		itemDefaultHandlers();
		return this;
	}

	public TestIndexOptions itemDefaultServices() {
		return addModule(new TestIndexDefaultModule());
	}

	public TestIndexOptions itemDefaultHandlers() {
		return addModule(new TestIndexHandlersModuleWithExtraInstances(handlers));
	}
	
	public TestIndexOptions addCoreDefault() {
		return this.addCore(new TestCore("repositem").setResources("se/repos/indexing/solr/repositem/**"));
	}
	
	public TestIndexOptions addCore(String identifier, String resourcePattern) {
		return this.addCore(new TestCore(identifier).setResources(resourcePattern));
	}
	
	public TestIndexOptions addCore(TestCore core) {
		if (coresUsed) {
			throw new IllegalStateException("Test indexing has already been initialized with cores, can not add new");
		}
		cores.put(core.getIdentifier(), core);
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
	
	public Iterable<TestCore> getCores() {
		coresUsed = true;
		return cores.values();
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
	
	/**
	 * Produces indexing configuration, for {@link ReposTestIndexing#enable(CmsTestRepository)}.
	 * 
	 * The returned configuration should exclude backend(s),
	 * configured in {@link se.repos.testing.ReposTestBackend},
	 * and solr cores,
	 * configured in {@link #addCore(TestCore)}.
	 * 
	 * @return collection that supports add (of backend module etc)
	 */
	protected Collection<Module> getConfiguration() {
		handlersUsed = true;
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
