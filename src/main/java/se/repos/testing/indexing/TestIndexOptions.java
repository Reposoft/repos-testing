/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServer;

import com.google.inject.Module;

import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.item.HandlerPathinfo;
import se.repos.indexing.item.HandlerProperties;
import se.repos.lgr.Lgr;
import se.repos.lgr.LgrFactory;
import se.repos.testing.indexing.config.TestIndexingDefaultConfig;
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
	
	public Set<IndexingItemHandler> getHandlers() {
		handlersUsed = true;
		return handlers;
	}
	
	/**
	 * Will be used once from {@link ReposTestIndexing#enable(CmsTestRepository)} to get indexing an possibly nearby services.
	 * @param repositem
	 * @return
	 */
	protected Module getConfiguration(SolrServer repositem) {
		return new TestIndexingDefaultConfig(repositem, getHandlers(), itemDefaultHandlers);
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
