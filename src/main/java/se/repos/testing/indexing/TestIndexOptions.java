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

import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.ItemPathinfo;
import se.repos.testing.indexing.config.TestIndexingDefaultConfig;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

/**
 * Carries no state, can be reused between tests if desired.
 */
public class TestIndexOptions {

	private Set<IndexingItemHandler> handlers = new LinkedHashSet<IndexingItemHandler>();

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
		// If we need to initialize handlers in a context that must be an earlier context than #getConfiguration
		this.addHandler(new ItemPathinfo());
		// TODO add the other handlers
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
	 * Must be done before {@link SvnTestIndexing#enable(CmsTestRepository)}
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
	 * Will be used once from {@link SvnTestIndexing#enable(CmsTestRepository)} to get indexing an possibly nearby services.
	 * @param repositem
	 * @return
	 */
	protected Module getConfiguration(SolrServer repositem) {
		return new TestIndexingDefaultConfig(repositem, getHandlers());
	}

	/**
	 * Notified about SvnTestIndexing tearDown.
	 */
	public void onTearDown() {
		coresUsed = false;
		handlersUsed = false;
	}
	
}
