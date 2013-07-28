package se.repos.indexing.testconfig;

import java.util.LinkedList;
import java.util.List;

import org.apache.solr.client.solrj.SolrServer;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.ItemPathinfo;
import se.repos.indexing.testing.TestIndexOptions;
import se.repos.indexing.twophases.ReposIndexingImpl;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

/**
 * Indexing configuration for our tests in this module.
 * See also the distributed standard config for unit tests, {@link TestIndexOptions#getIndexing()}.
 */
public class IndexingTestModule extends AbstractModule {

	private SolrServer repositem;

	public IndexingTestModule(SolrServer solrRepositemCore) {
		this.repositem = solrRepositemCore;
	}
	
	@Override
	protected void configure() {
		bind(SolrServer.class).annotatedWith(Names.named("repositem"))
			.toInstance(repositem);
		
		bind(ReposIndexing.class).to(ReposIndexingImpl.class);
		
		List<IndexingItemHandler> blocking = new LinkedList<IndexingItemHandler>();
		blocking.add(new ItemPathinfo()); // when we need injections to indexers we must use Multibinder
		List<IndexingItemHandler> background = new LinkedList<IndexingItemHandler>();
		bind(new TypeLiteral<Iterable<IndexingItemHandler>>(){}).annotatedWith(Names.named("blocking")).toInstance(blocking);
		bind(new TypeLiteral<Iterable<IndexingItemHandler>>(){}).annotatedWith(Names.named("background")).toInstance(background);
		
		// backend-svnkit
		bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLook.class);
	}

}
