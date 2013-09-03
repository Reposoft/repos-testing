/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.apache.solr.client.solrj.SolrServer;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.ItemPathinfo;
import se.repos.indexing.twophases.ItemContentsNocache;
import se.repos.indexing.twophases.ItemPropertiesImmediate;
import se.repos.indexing.twophases.ReposIndexingImpl;
import se.repos.testing.indexing.solr.TestIndexServerSolrEmbedded;
import se.repos.testing.indexing.svn.SvnTestIndexing;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsRepositoryLookupSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

public class TestIndexOptions {

	private Set<IndexingItemHandler> handlers = new LinkedHashSet<IndexingItemHandler>();

	private Map<String, String> cores = new HashMap<String, String>();
	
	private Map<String, String> aliases = new HashMap<String, String>();

	private TestIndexServer server = null;

	private ReposIndexing indexing = null;
	
	/**
	 * Set up for basic "repositem" blocking indexing, i.e. structure but not contents.
	 * @return for chaining
	 */
	public TestIndexOptions itemDefaults() {
		this.addHandler(new ItemPathinfo());
		this.addCore("repositem", "se/repos/indexing/solr/repositem/**");
		this.server = new TestIndexServerSolrEmbedded();
		return this;
	}
	
	public TestIndexOptions addHandler(IndexingItemHandler handler) {
		this.handlers.add(handler);
		return this;
	}
	
	public TestIndexOptions addCore(String identifier, String resourcePattern) {
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
	
	public Set<IndexingItemHandler> getHandlers() {
		return handlers;
	}

	public Map<String, String> getCores() {
		return cores;
	}
	
	/**
	 * Meant for internal use.
	 */
	public TestIndexServer getServer() {
		return server;
	}
	
	/**
	 * Loads a solr core that was configured and cleared at {@link #getInstance(TestIndexOptions)}.
	 * To get live index data in tests, first call {@link #enable(CmsTestRepository)}.
	 * @param identifier our internal core name, though maybe suffixed in Solr
	 * @return direct Solr access to the core
	 */
	public SolrServer getCore(String identifier) {
		TestIndexOptions options = this;
		if (!options.hasCore(identifier)) {
			throw new IllegalArgumentException("Core '" + identifier + "' not found in test cores " + options.getCores().keySet());
		}
		return this.server.getCore(identifier);
	}

	public void tearDown() {
		this.server.destroy();
		this.server = null;
		this.indexing = null;
	}
	
	public ReposIndexing getIndexing() {
		return getIndexing(getCore("repositem"));
	}	
	
	/**
	 * Allows override of the configuration for test indexing,
	 * though preferrably tests should only need {@link #addHandler(IndexingItemHandler)}.
	 * 
	 * TODO we wanted to avoid using a DI framework when testing was a part of repos-indexing
	 * but now that testing will always be used in scope test we could have sisu-guice as runtime dependency
	 * 
	 * Very geared towards {@link SvnTestIndexing}.
	 * Subclasses could use a real injection module.
	 * See {@link se.repos.testing.indexing.testconfig.IndexingTestModule}.
	 * 
	 * @param repositem The fundamental core needed for indexing to run
	 * @return configured indexing for current test backend
	 */
	protected ReposIndexing getIndexing(SolrServer repositem) {
		// We're assuming here that repositem doesn't change
		if (this.indexing == null) {
			this.indexing = getIndexingNew(repositem);
		}
		return this.indexing;
	}
	
	protected ReposIndexing getIndexingNew(SolrServer repositem) {
		// Backend
		//bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		Provider<SVNLookClient> svnlook = new SvnlookClientProviderStateless();
		//bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLook.class);
		CmsChangesetReaderSvnkitLook reader = new CmsChangesetReaderSvnkitLook();
		reader.setSVNLookClientProvider(svnlook);
		CmsContentsReaderSvnkitLook contents = new CmsContentsReaderSvnkitLook();
		contents.setSVNLookClientProvider(svnlook);
		CmsRepositoryLookupSvnkitLook lookup = new CmsRepositoryLookupSvnkitLook();
		lookup.setSVNLookClientProvider(svnlook);
		// Indexing
		ReposIndexingImpl impl = new ReposIndexingImpl();
		impl.setCmsChangesetReader(reader);
		impl.setItemBlocking(getHandlers());
		impl.setSolrRepositem(repositem);
		impl.setItemContentsBufferStrategy(new ItemContentsNocache().setCmsContentsReader(contents));
		impl.setItemPropertiesBufferStrategy(new ItemPropertiesImmediate().setCmsContentsReader(contents));
		impl.setRevisionLookup(lookup);
		return impl;
	}
	
}
