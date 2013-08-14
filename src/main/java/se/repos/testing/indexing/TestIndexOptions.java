package se.repos.testing.indexing;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.apache.solr.client.solrj.SolrServer;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.ItemPathinfo;
import se.repos.indexing.twophases.ItemContentsNocache;
import se.repos.indexing.twophases.ReposIndexingImpl;
import se.repos.testing.indexing.svn.SvnTestIndexing;
import se.repos.testing.indexing.testconfig.IndexingTestModule;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;

public class TestIndexOptions {

	private List<IndexingItemHandler> handlers = new LinkedList<IndexingItemHandler>();

	private Map<String, String> cores = new HashMap<String, String>();
	
	private Map<String, String> aliases = new HashMap<String, String>();
	
	/**
	 * Set up for basic "repositem" blocking indexing, i.e. structure but not contents.
	 * @return for chaining
	 */
	public TestIndexOptions itemDefaults() {
		this.addHandler(new ItemPathinfo());
		this.addCore("repositem", "se/repos/indexing/solr/repositem/**");
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
	
	public List<IndexingItemHandler> getHandlers() {
		return handlers;
	}

	public Map<String, String> getCores() {
		return cores;
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
	 * See {@link IndexingTestModule}.
	 * 
	 * @param repositem The fundamental core needed for indexing to run
	 * @return configured indexing for current test backend
	 */
	public ReposIndexing getIndexing(SolrServer repositem) {
		// Backend
		//bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		Provider<SVNLookClient> svnlook = new SvnlookClientProviderStateless();
		//bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLook.class);
		CmsChangesetReaderSvnkitLook reader = new CmsChangesetReaderSvnkitLook();
		reader.setSVNLookClientProvider(svnlook);
		CmsContentsReaderSvnkitLook contents = new CmsContentsReaderSvnkitLook();
		contents.setSVNLookClientProvider(svnlook);
		// Indexing
		ReposIndexingImpl impl = new ReposIndexingImpl();
		impl.setCmsChangesetReader(reader);
		impl.setItemBlocking(getHandlers());
		impl.setSolrRepositem(repositem);
		impl.setItemContentsBufferStrategy(new ItemContentsNocache().setCmsContentsReader(contents));
		return impl;
	}
	
}